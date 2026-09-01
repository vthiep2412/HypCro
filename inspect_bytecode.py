import os
import glob
import re
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

def main():
    jar_path = find_minecraft_jar()
    args_file = "inspect_query.txt"
    if not os.path.exists(args_file):
        print(f"Error: {args_file} does not exist.")
        sys.exit(1)
        
    with open(args_file, "r", encoding="utf-8") as f:
        lines = [l.strip() for l in f.readlines() if l.strip() and not l.strip().startswith("#")]
        
    if not lines:
        print("No classes or arguments found in inspect_query.txt")
        sys.exit(1)
        
    cmd_args = []
    has_flags = False
    for line in lines:
        parts = line.split()
        for p in parts:
            if p.startswith("-"):
                has_flags = True
            cmd_args.append(p)
            
    base_cmd = ["javap"]
    if not has_flags or "-p" not in cmd_args:
        base_cmd.append("-p")
    base_cmd.extend(["-cp", jar_path])
    base_cmd.extend(cmd_args)
    
    result = subprocess.run(base_cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.stdout:
        print(result.stdout)
    if result.stderr:
        print("STDERR:\n" + result.stderr)

if __name__ == "__main__":
    main()
