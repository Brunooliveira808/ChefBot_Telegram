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
        if (text.equalsIgnoreCase("/start") || text.equalsIgnoreCase("/ajuda") || text.equalsIgnoreCase("/help")) {
            sendHelpMessage(chatId, userName);
            return;
        }

        if (text.equalsIgnoreCase("/receitas") || text.equalsIgnoreCase("/minhasreceitas")) {
            listAllRecipes(chatId);
            return;
        }

        if (text.toLowerCase().startsWith("/buscar ")) {
            String query = text.substring(8).trim();
            searchRecipes(chatId, query);
            return;
        }

        // Se for texto comum, assume que é uma receita sendo informada
        sendTextMessage(chatId, "✍️ Lendo sua receita e organizando os passos com IA...");

        try {
            RecipeResponseDTO dto = geminiService.extractRecipeFromText(text);
            Recipe savedRecipe = recipeService.saveRecipeFromDTO(chatId, userName, dto, text);

            String formattedResponse = "✅ *Receita salva com sucesso!*\n\n" + 
                                       recipeService.formatRecipeForTelegram(savedRecipe);
            sendTextMessage(chatId, formattedResponse);
        } catch (Exception e) {
            log.error("Falha ao estruturar receita por texto: {}", e.getMessage(), e);
            sendTextMessage(chatId, "❌ Não consegui identificar a receita. Tente enviar com mais detalhes.");
        }
    }

    private void handleVoiceMessage(Long chatId, String userName, String fileId) {
        sendTextMessage(chatId, "🎧 Ouvindo seu áudio e anotando a receita...");

        try {
            // Baixa o áudio enviado pelo Telegram
            GetFile getFile = new GetFile(fileId);
            File file = telegramClient.execute(getFile);

            byte[] audioBytes;
            try (InputStream is = telegramClient.downloadFileAsStream(file)) {
                audioBytes = is.readAllBytes();
            }

            // Envia para o Gemini transcrever e estruturar
            RecipeResponseDTO dto = geminiService.extractRecipeFromAudio(audioBytes, "audio/ogg");
            Recipe savedRecipe = recipeService.saveRecipeFromDTO(chatId, userName, dto, "[Áudio gravado]");

            String formattedResponse = "✅ *Áudio processado e receita salva!*\n\n" + 
                                       recipeService.formatRecipeForTelegram(savedRecipe);
            sendTextMessage(chatId, formattedResponse);
        } catch (Exception e) {
            log.error("Falha ao processar áudio: {}", e.getMessage(), e);
            sendTextMessage(chatId, "❌ Ocorreu um erro ao ouvir o áudio. Verifique se a gravação está audível e tente novamente.");
        }
    }

    private void listAllRecipes(Long chatId) {
        List<Recipe> recipes = recipeService.findRecipesByChatId(chatId);
        if (recipes.isEmpty()) {
            sendTextMessage(chatId, "📖 Você ainda não tem nenhuma receita salva.\n\nEnvie um áudio ou texto ensinando uma receita para começar!");
            return;
        }

        StringBuilder sb = new StringBuilder("📚 *Suas Receitas Salvas:*\n\n");
        for (int i = 0; i < recipes.size(); i++) {
            Recipe r = recipes.get(i);
            sb.append(i + 1).append(". *").append(r.getTitle()).append("*");
            if (r.getSourceAuthor() != null && !r.getSourceAuthor().isBlank()) {
                sb.append(" (").append(r.getSourceAuthor()).append(")");
            }
            sb.append("\n");
        }
        sb.append("\n💡 Digite `/buscar <nome>` para ver os detalhes de uma receita.");
        sendTextMessage(chatId, sb.toString());
    }

    private void searchRecipes(Long chatId, String query) {
        List<Recipe> recipes = recipeService.searchRecipesByTitle(chatId, query);
        if (recipes.isEmpty()) {
            sendTextMessage(chatId, "🔍 Nenhuma receita encontrada com o termo: *" + query + "*");
            return;
        }

        for (Recipe r : recipes) {
            sendTextMessage(chatId, recipeService.formatRecipeForTelegram(r));
        }
    }

    private void sendHelpMessage(Long chatId, String userName) {
        String msg = String.format("""
            👨‍🍳 *Olá, %s! Bem-vindo ao ChefBot!*

            Eu sou o seu caderno inteligente de receitas.
            
            Como me usar:
            🎙️ *Áudio:* Mande um áudio explicando uma receita (ou grave sua mãe/tia falando).
            ✍️ *Texto:* Digite os ingredientes e o modo de preparo de forma livre.
            
            Comandos úteis:
            • `/receitas` - Lista todas as suas receitas salvas.
            • `/buscar <nome>` - Busca uma receita específica por nome.
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
                    .text(text.replace("*", ""))
                    .build();
                telegramClient.execute(fallback);
            } catch (Exception ex) {
                log.error("Erro fatal ao enviar mensagem Telegram para chatId {}: {}", chatId, ex.getMessage());
            }
        }
    }
}
