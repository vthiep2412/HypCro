package com.hypcro.mixins;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {

    @Shadow @Final private EditBox input;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow public abstract void showSuggestions(boolean narrate);

    private static final List<String> ROOT_COMMANDS = List.of(
        ".hypcro",
        ".hypcroinspectblock",
        ".hypcrobot",
        ".hypcrobitstar",
        ".hypcrogettablist",
        ".hypcrogetscoreboard",
        ".hypcropathfindverbose",
        ".hypcrotest"
    );

    private static final Map<String, List<String>> SUB_ARGUMENTS = Map.of(
        ".hypcrotest", List.of("movecam", "flyto", "pathfind", "party", "currentyear"),
        ".hypcropathfindverbose", List.of("true", "false", "on", "off"),
        ".hypcrobot", List.of("v2", "classic", "new", "old")
    );

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void onUpdateCommandInfo(CallbackInfo ci) {
        String text = this.input.getValue();
        if (!text.startsWith(".")) return;

        int cursor = this.input.getCursorPosition();
        String prefix = text.substring(0, cursor);

        int lastSpace = prefix.lastIndexOf(' ');
        int startOffset = (lastSpace == -1) ? 0 : lastSpace + 1;
        String currentWord = prefix.substring(startOffset).toLowerCase();

        SuggestionsBuilder builder = new SuggestionsBuilder(text, startOffset);

        if (lastSpace == -1) {
            for (String cmd : ROOT_COMMANDS) {
                if (cmd.startsWith(currentWord)) {
                    builder.suggest(cmd);
                }
            }
        } else {
            String rootCmd = prefix.substring(0, lastSpace).trim().toLowerCase();
            List<String> subs = SUB_ARGUMENTS.get(rootCmd);
            if (subs != null) {
                for (String sub : subs) {
                    if (sub.startsWith(currentWord)) {
                        builder.suggest(sub);
                    }
                }
            }
        }

        this.pendingSuggestions = builder.buildFuture();
        this.showSuggestions(false);
        ci.cancel();
    }
}
