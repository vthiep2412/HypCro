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
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {

    @Shadow @Final private EditBox input;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow public abstract void showSuggestions(boolean narrate);

    private static final List<String> DOT_COMMANDS = List.of(
        ".hypcro",
        ".hypcrobot ",
        ".hypcrobitstar ",
        ".hypcrogettablist",
        ".hypcrogetscoreboard",
        ".hypcropathfindverbose",
        ".hypcrotest movecam ",
        ".hypcrotest flyto ",
        ".hypcrotest pathfind "
    );

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void onUpdateCommandInfo(CallbackInfo ci) {
        String text = this.input.getValue();
        if (text.startsWith(".")) {
            int cursor = this.input.getCursorPosition();
            String prefix = text.substring(0, cursor);

            SuggestionsBuilder builder = new SuggestionsBuilder(text, 0);
            for (String cmd : DOT_COMMANDS) {
                if (cmd.toLowerCase().startsWith(prefix.toLowerCase())) {
                    builder.suggest(cmd);
                }
            }

            this.pendingSuggestions = builder.buildFuture();
            this.showSuggestions(false);
            ci.cancel();
        }
    }
}
