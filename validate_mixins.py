import os
import glob
import re
import json
import subprocess
import sys

def get_minecraft_version():
    build_gradle = "build.gradle.kts"
    if os.path.exists(build_gradle):
        with open(build_gradle, "r", encoding="utf-8") as f:
            content = f.read()
            match = re.search(r'minecraft\("com\.mojang:minecraft:([^"]+)"\)', content)
            if match:
                return match.group(1).strip()
    return None

def find_minecraft_jar():
    version = get_minecraft_version()
    if version:
        candidates = glob.glob(os.path.join(".gradle", "loom-cache", "minecraftMaven", "net", "minecraft", "**", version, "*merged*.jar"), recursive=True)
        if candidates:
            return candidates[0]
    candidates = glob.glob(os.path.join(".gradle", "loom-cache", "minecraftMaven", "net", "minecraft", "**", "*merged*.jar"), recursive=True)
    if not candidates:
        raise FileNotFoundError("Could not find Loom merged Minecraft JAR in .gradle cache")
    return candidates[0]

def get_class_members(jar_path, class_name):
    """Runs javap against class in jar and parses method names and fields."""
    cmd = ["javap", "-p", "-cp", jar_path, class_name]
    res = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if res.returncode != 0:
        return None, res.stderr
    
    methods = set()
    fields = set()
    raw_lines = res.stdout.splitlines()
    
    for line in raw_lines:
        line = line.strip().rstrip(";")
        if not line or line.startswith("//") or line.startswith("Compiled from") or line.startswith("public class") or line.startswith("public interface") or line.startswith("}"):
            continue
        if "(" in line and ")" in line:
            # Method signature e.g. public void handleMovePlayer(net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket);
            sig = line.split("(")[0].strip()
            name = sig.split()[-1]
            methods.add(name)
        else:
            # Field e.g. private int rightClickDelay;
            parts = line.split()
            if len(parts) >= 2:
                fields.add(parts[-1])
                
    return {"methods": methods, "fields": fields, "raw": res.stdout}, None

def parse_mixin_file(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Find imports
    imports = {}
    for match in re.finditer(r'import\s+([\w\.\$]+);', content):
        full_name = match.group(1)
        simple_name = full_name.split(".")[-1]
        imports[simple_name] = full_name

    # Find @Mixin(Target.class) or @Mixin(value = Target.class, ...)
    mixin_match = re.search(r'@Mixin\s*\(\s*(?:value\s*=\s*)?([\w\.]+)\.class', content)
    target_class = None
    if mixin_match:
        target_name = mixin_match.group(1).strip()
        target_class = imports.get(target_name, target_name)

    # Find @Inject, @ModifyExpressionValue, @ModifyReturnValue, @Redirect, @WrapOperation
    injects = []
    for match in re.finditer(r'@(?:Inject|ModifyExpressionValue|ModifyReturnValue|Redirect|WrapOperation)\s*\((.*?)\)\s*(?:[\w\s<>\[\]@\(\)]+?\s+)?(\w+)\s*\(([^)]*)\)', content, re.DOTALL):
        anno_body = match.group(1)
        handler_name = match.group(2)
        params = match.group(3).strip()
        method_match = re.search(r'method\s*=\s*"([^"]+)"', anno_body)
        if method_match:
            method_target = method_match.group(1)
            injects.append({
                "target": method_target,
                "handler": handler_name,
                "params": params,
                "is_inject": "@Inject" in match.group(0)
            })

    # Find @Accessor
    accessors = []
    for match in re.finditer(r'@Accessor\s*\(\s*(?:value\s*=\s*)?(?:"([^"]+)")?\s*\)\s*(?:[\w\s<>\[\]@]+\s+)?(\w+)\s*\(', content):
        target = match.group(1)
        name = match.group(2)
        if not target:
            target = name
            for prefix in ("get", "set", "is"):
                if target.startswith(prefix) and len(target) > len(prefix):
                    target = target[len(prefix):]
                    target = target[0].lower() + target[1:]
                    break
        accessors.append({"target": target, "method": name})

    # Find @Invoker
    invokers = []
    for match in re.finditer(r'@Invoker\s*\(\s*(?:value\s*=\s*)?(?:"([^"]+)")?\s*\)\s*(?:[\w\s<>\[\]@]+\s+)?(\w+)\s*\(', content):
        target = match.group(1)
        name = match.group(2)
        if not target:
            target = name
            if target.startswith("invoke") and len(target) > 6:
                target = target[6:]
                target = target[0].lower() + target[1:]
            elif target.startswith("call") and len(target) > 4:
                target = target[4:]
                target = target[0].lower() + target[1:]
        invokers.append({"target": target, "method": name})

    return {
        "file": file_path,
        "target_class": target_class,
        "injects": injects,
        "accessors": accessors,
        "invokers": invokers
    }

def main():
    log_lines = []
    def log(msg):
        print(msg)
        log_lines.append(msg)

    log("=" * 70)
    log(" HypCro Lightweight Mixin Bytecode Validator")
    log("=" * 70)

    try:
        jar_path = find_minecraft_jar()
        log(f"[INFO] Using Minecraft JAR: {os.path.basename(jar_path)}")
    except Exception as e:
        log(f"[FATAL] {e}")
        sys.exit(1)

    # 1. Check hypcro.mixins.json
    config_path = "src/main/resources/hypcro.mixins.json"
    if not os.path.exists(config_path):
        log(f"[FATAL] Missing mixin configuration at {config_path}")
        sys.exit(1)

    with open(config_path, "r", encoding="utf-8") as f:
        config_data = json.load(f)

    registered_mixins = set(config_data.get("client", []) + config_data.get("mixins", []))
    log(f"[INFO] Registered mixins in hypcro.mixins.json: {len(registered_mixins)}")

    # 2. Find all mixin files on disk
    mixin_dir = "src/main/java/com/hypcro/mixins"
    disk_files = glob.glob(os.path.join(mixin_dir, "*.java"))
    disk_names = {os.path.splitext(os.path.basename(p))[0]: p for p in disk_files}

    errors = 0
    warnings = 0
    total_checks = 0

    # Check for un-registered mixin files
    for name in disk_names:
        if name not in registered_mixins:
            log(f"[WARN] File '{name}.java' exists on disk but is NOT registered in hypcro.mixins.json!")
            warnings += 1

    # Check for missing mixin files on disk
    for name in registered_mixins:
        if name not in disk_names:
            log(f"[ERROR] Mixin '{name}' is in hypcro.mixins.json but file does not exist on disk!")
            errors += 1

    class_cache = {}
    verified_mixins = 0

    log("-" * 70)
    for name, file_path in sorted(disk_names.items()):
        parsed = parse_mixin_file(file_path)
        target = parsed["target_class"]

        if not target:
            log(f"[WARN] Could not determine @Mixin target class in {name}.java")
            warnings += 1
            continue

        total_checks += 1
        # Fetch target class members from JAR
        if target not in class_cache:
            members, err = get_class_members(jar_path, target)
            if err or members is None:
                log(f"[ERROR] [{name}.java] Target class '{target}' could not be loaded from Minecraft JAR!")
                errors += 1
                continue
            class_cache[target] = members

        members = class_cache[target]
        has_issue = False

        # Validate @Inject targets
        for inj in parsed["injects"]:
            raw_target = inj["target"]
            target_method = raw_target.split("(")[0].strip()
            total_checks += 1
            if target_method != "<init>" and target_method not in members["methods"]:
                log(f"[ERROR] [{name}.java] Target method '{raw_target}' not found in '{target}'!")
                errors += 1
                has_issue = True

            # Verify CallbackInfo in parameters for @Inject
            if inj.get("is_inject", True) and "CallbackInfo" not in inj["params"]:
                log(f"[ERROR] [{name}.java] Inject handler '{inj['handler']}' missing CallbackInfo/CallbackInfoReturnable in parameters!")
                errors += 1
                has_issue = True

        # Validate @Accessor targets
        for acc in parsed["accessors"]:
            target_field = acc["target"]
            total_checks += 1
            if target_field not in members["fields"]:
                log(f"[ERROR] [{name}.java] Target field '{target_field}' not found in '{target}'!")
                errors += 1
                has_issue = True

        # Validate @Invoker targets
        for inv in parsed["invokers"]:
            raw_target = inv["target"]
            target_method = raw_target.split("(")[0].strip()
            total_checks += 1
            if target_method not in members["methods"]:
                log(f"[ERROR] [{name}.java] Invoker target method '{raw_target}' not found in '{target}'!")
                errors += 1
                has_issue = True

        if not has_issue:
            verified_mixins += 1
            status = f"OK ({len(parsed['injects'])} injects, {len(parsed['accessors'])} accessors, {len(parsed['invokers'])} invokers)"
            log(f"  [PASS] {name} -> {target.split('.')[-1]} [{status}]")

    log("-" * 70)
    log(f"[SUMMARY] Verified Mixins: {verified_mixins}/{len(disk_names)} | Total Checks: {total_checks} | Errors: {errors} | Warnings: {warnings}")

    # Write log to build directory for AI agents / tools
    os.makedirs("build", exist_ok=True)
    with open("build/mixin_check.log", "w", encoding="utf-8") as f:
        f.write("\n".join(log_lines) + "\n")

    log(f"[INFO] Full audit written to build/mixin_check.log")
    log("=" * 70)

    if errors > 0:
        log("[FAIL] Mixin validation encountered errors!")
        sys.exit(1)
    else:
        log("[SUCCESS] All mixin targets and descriptors are valid.")
        sys.exit(0)

if __name__ == "__main__":
    main()
