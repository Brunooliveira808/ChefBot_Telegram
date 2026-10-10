package ChefBot_Telegram.service;

import ChefBot_Telegram.dto.RecipeResponseDTO;
import ChefBot_Telegram.model.Recipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Component
public class TelegramBotService implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    private final String botToken;
    private final TelegramClient telegramClient;
    private final GeminiService geminiService;
    private final RecipeService recipeService;

    public TelegramBotService(
            @Value("${telegram.bot.token}") String botToken,
            GeminiService geminiService,
            RecipeService recipeService) {
        this.botToken = botToken;
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.geminiService = geminiService;
        this.recipeService = recipeService;
    }

    @Override
    public String getBotToken() {
        return this.botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        Long chatId = message.getChatId();
        String userName = message.getFrom() != null ? message.getFrom().getFirstName() : "Usuário";

        try {
            // 1. Tratamento de Mensagem de ÁUDIO / VOZ
            if (message.hasVoice()) {
                handleVoiceMessage(chatId, userName, message.getVoice().getFileId());
            } 
            // 2. Tratamento de Mensagem de TEXTO
            else if (message.hasText()) {
                handleTextMessage(chatId, userName, message.getText().trim());
            }
        } catch (Exception e) {
            log.error("Erro ao processar mensagem do Telegram para o chatId {}: {}", chatId, e.getMessage(), e);
            sendTextMessage(chatId, "⚠️ Desculpe, tive um problema ao processar sua mensagem. Tente novamente.");
        }
    }

    private void handleTextMessage(Long chatId, String userName, String text) {
        if (text.startsWith("/")) {
            String[] parts = text.split("\\s+", 2);
            String rawCmd = parts[0].toLowerCase();
            String cmd = rawCmd.contains("@") ? rawCmd.substring(0, rawCmd.indexOf('@')) : rawCmd;
            String args = parts.length > 1 ? parts[1].trim() : "";

            switch (cmd) {
                case "/start", "/ajuda", "/help" -> sendHelpMessage(chatId, userName);
                case "/receitas", "/minhasreceitas" -> listAllRecipes(chatId);
                case "/buscar", "/pesquisar" -> handleSearchCommand(chatId, args);
                case "/atualizar", "/editar" -> handleUpdateCommand(chatId, args);
                case "/deletar", "/apagar", "/excluir", "/remover" -> handleDeleteCommand(chatId, args);
                default -> sendTextMessage(chatId, "❓ Comando não reconhecido. Digite `/ajuda` para ver os comandos disponíveis.");
            }
            return;
        }

        // Se for texto comum, passa pelo Gemini para validação e extração
        sendTextMessage(chatId, "✍️ Analisando sua mensagem com IA...");

        try {
            RecipeResponseDTO dto = geminiService.extractRecipeFromText(text);

            if (dto.isRecipe() == null || !dto.isRecipe()) {
                sendTextMessage(chatId, "🧑‍🍳 Não identifiquei uma receita culinária na sua mensagem.\n\nEnvie uma receita em texto ou áudio (com ingredientes ou modo de preparo), ou use `/ajuda` para ver os comandos.");
                return;
            }

            Recipe savedRecipe = recipeService.saveRecipeFromDTO(chatId, userName, dto, text);

            String formattedResponse = "✅ *Receita salva com sucesso!*\n\n" + 
                                       recipeService.formatRecipeForTelegram(savedRecipe) +
                                       "\n💡 *Dica:* Para alterar algum dado no futuro, use `/atualizar " + savedRecipe.getId() + " <o que mudar>`";
            sendTextMessage(chatId, formattedResponse);
        } catch (Exception e) {
            log.error("Falha ao processar receita por texto: {}", e.getMessage(), e);
            sendTextMessage(chatId, "❌ Ocorreu um erro ao processar a receita. Tente novamente em instantes.");
        }
    }

    private void handleVoiceMessage(Long chatId, String userName, String fileId) {
        sendTextMessage(chatId, "🎧 Ouvindo seu áudio e analisando com IA...");

        try {
            GetFile getFile = new GetFile(fileId);
            File file = telegramClient.execute(getFile);

            byte[] audioBytes;
            try (InputStream is = telegramClient.downloadFileAsStream(file)) {
                audioBytes = is.readAllBytes();
            }

            RecipeResponseDTO dto = geminiService.extractRecipeFromAudio(audioBytes, "audio/ogg");

            if (dto.isRecipe() == null || !dto.isRecipe()) {
                sendTextMessage(chatId, "🧑‍🍳 Não identifiquei uma receita culinária no áudio.\n\nPor favor, grave um áudio explicando a receita (ingredientes ou modo de preparo).");
                return;
            }

            Recipe savedRecipe = recipeService.saveRecipeFromDTO(chatId, userName, dto, "[Áudio gravado]");

            String formattedResponse = "✅ *Áudio processado e receita salva!*\n\n" + 
                                       recipeService.formatRecipeForTelegram(savedRecipe) +
                                       "\n💡 *Dica:* Para alterar algum dado no futuro, use `/atualizar " + savedRecipe.getId() + " <o que mudar>`";
            sendTextMessage(chatId, formattedResponse);
        } catch (Exception e) {
            log.error("Falha ao processar áudio: {}", e.getMessage(), e);
            sendTextMessage(chatId, "❌ Ocorreu um erro ao ouvir o áudio. Verifique se a gravação está audível e tente novamente.");
        }
    }

    private void handleSearchCommand(Long chatId, String args) {
        if (args.isBlank()) {
            sendTextMessage(chatId, """
                🔍 *Como buscar receitas:*

                Digite `/buscar <termo>` informando o nome, ingrediente, categoria ou ID da receita.

                *Exemplos:*
                • `/buscar bolo`
                • `/buscar cenoura`
                • `/buscar sobremesa`
                • `/buscar 1`
                """);
            return;
        }

        List<Recipe> recipes = recipeService.searchRecipes(chatId, args);
        if (recipes.isEmpty()) {
            sendTextMessage(chatId, "🔍 Nenhuma receita encontrada para o termo: *" + args + "*\n\nUse `/receitas` para ver todas as suas receitas salvas.");
            return;
        }

        for (Recipe r : recipes) {
            sendTextMessage(chatId, recipeService.formatRecipeForTelegram(r));
        }
    }

    private void handleUpdateCommand(Long chatId, String args) {
        if (args.isBlank()) {
            sendTextMessage(chatId, """
                ✏️ *Como atualizar uma receita:*

                Digite: `/atualizar <ID> <o que deseja alterar>`

                *Exemplos de uso:*
                • `/atualizar 1 Mudar o autor para Minha Mãe`
                • `/atualizar 1 Trocar 2 xícaras de açúcar por 1 xícara de adoçante culinário`
                • `/atualizar 1 Mudar o título para Bolo de Cenoura Especial`
                • `/atualizar 1 O tempo de preparo é de 40 minutos`
                • `/atualizar 1 Acrescentar 1 pitada de sal nos ingredientes`

                💡 Use `/receitas` para ver o número de ID de cada receita salva.
                """);
            return;
        }

        String[] parts = args.split("\\s+", 2);
        String idStr = parts[0].replace("#", "").trim();
        String instructions = parts.length > 1 ? parts[1].trim() : "";

        Long recipeId;
        try {
            recipeId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            sendTextMessage(chatId, "❌ ID inválido. Use um número (ex: `/atualizar 1 ...`).");
            return;
        }

        Optional<Recipe> optionalRecipe = recipeService.findRecipeByUserAndId(chatId, recipeId);
        if (optionalRecipe.isEmpty()) {
            sendTextMessage(chatId, "❌ Receita com ID #" + recipeId + " não foi encontrada no seu caderno de receitas.");
            return;
        }

        Recipe existingRecipe = optionalRecipe.get();

        if (instructions.isBlank()) {
            sendTextMessage(chatId, "📖 *Receita encontrada:*\n\n" + 
                recipeService.formatRecipeForTelegram(existingRecipe) +
                "\n✏️ *Para atualizá-la, envie:* `/atualizar " + recipeId + " <o que deseja alterar>`");
            return;
        }

        sendTextMessage(chatId, "🔄 Atualizando a receita *" + existingRecipe.getTitle() + "* com suas alterações...");

        try {
            RecipeResponseDTO updatedDTO = geminiService.updateRecipe(existingRecipe, instructions);
            Recipe updatedRecipe = recipeService.updateRecipeFromDTO(chatId, recipeId, updatedDTO);

            String responseMsg = "✅ *Receita atualizada com sucesso!*\n\n" + 
                                 recipeService.formatRecipeForTelegram(updatedRecipe);
            sendTextMessage(chatId, responseMsg);
        } catch (Exception e) {
            log.error("Erro ao atualizar receita #{}: {}", recipeId, e.getMessage(), e);
            sendTextMessage(chatId, "❌ Ocorreu um erro ao atualizar a receita. Tente novamente com mais detalhes.");
        }
    }

    private void handleDeleteCommand(Long chatId, String args) {
        if (args.isBlank()) {
            sendTextMessage(chatId, """
                🗑️ *Como deletar uma receita:*

                Digite: `/deletar <ID>` informando o número da receita.

                *Exemplo:*
                • `/deletar 1`

                💡 Use `/receitas` para consultar os IDs das suas receitas.
                """);
            return;
        }

        String idStr = args.replace("#", "").trim();
        Long recipeId;
        try {
            recipeId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            sendTextMessage(chatId, "❌ ID inválido. Use um número (ex: `/deletar 1`).");
            return;
        }

        Optional<Recipe> optionalRecipe = recipeService.findRecipeByUserAndId(chatId, recipeId);
        if (optionalRecipe.isEmpty()) {
            sendTextMessage(chatId, "❌ Receita com ID #" + recipeId + " não foi encontrada no seu caderno de receitas.");
            return;
        }

        String title = optionalRecipe.get().getTitle();
        boolean deleted = recipeService.deleteRecipe(chatId, recipeId);

        if (deleted) {
            sendTextMessage(chatId, "🗑️ *Receita `[#" + recipeId + "] " + title + "` foi excluída com sucesso!*");
        } else {
            sendTextMessage(chatId, "❌ Não foi possível excluir a receita. Tente novamente.");
        }
    }

    private void listAllRecipes(Long chatId) {
        List<Recipe> recipes = recipeService.findRecipesByChatId(chatId);
        if (recipes.isEmpty()) {
            sendTextMessage(chatId, "📖 Você ainda não tem nenhuma receita salva.\n\nEnvie um áudio ou texto ensinando uma receita para começar!");
            return;
        }

        StringBuilder sb = new StringBuilder("📚 *Suas Receitas Salvas:*\n\n");
        for (Recipe r : recipes) {
            sb.append("• `[#").append(r.getId()).append("]` *").append(r.getTitle()).append("*");
            if (r.getSourceAuthor() != null && !r.getSourceAuthor().isBlank()) {
                sb.append(" _(por ").append(r.getSourceAuthor()).append(")_");
            }
            sb.append("\n");
        }
        sb.append("\n💡 *Dicas:*\n• Digite `/buscar <nome ou ID>` para ver os detalhes da receita.\n• Digite `/atualizar <ID> <alteração>` para editar qualquer informação.\n• Digite `/deletar <ID>` para apagar uma receita.");
        sendTextMessage(chatId, sb.toString());
    }

    private void sendHelpMessage(Long chatId, String userName) {
        String msg = String.format("""
            👨‍🍳 *Olá, %s! Bem-vindo ao ChefBot!*

            Eu sou o seu caderno inteligente de receitas.
            
            *Como salvar novas receitas:*
            🎙️ *Áudio:* Mande um áudio explicando uma receita com ingredientes e preparo.
            ✍️ *Texto:* Envie o texto da receita de forma livre.
            
            *Comandos disponíveis:*
            • `/receitas` - Lista todas as suas receitas salvas com seus IDs.
            • `/buscar <termo>` - Busca receitas por nome, ingrediente, categoria ou ID.
            • `/atualizar <ID> <alteração>` - Atualiza título, autor, ingredientes ou preparo de uma receita existente.
            • `/deletar <ID>` - Exclui uma receita do seu caderno.
            • `/ajuda` - Mostra esta mensagem de ajuda.
            """, userName);
        sendTextMessage(chatId, msg);
    }

    private void sendTextMessage(Long chatId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("Markdown")
                .build();
            telegramClient.execute(message);
        } catch (Exception e) {
            log.warn("Erro ao enviar mensagem Markdown, tentando texto puro: {}", e.getMessage());
            try {
                SendMessage fallback = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text.replace("*", "").replace("`", "").replace("_", ""))
                    .build();
                telegramClient.execute(fallback);
            } catch (Exception ex) {
                log.error("Erro fatal ao enviar mensagem Telegram para chatId {}: {}", chatId, ex.getMessage());
            }
        }
    }
}


