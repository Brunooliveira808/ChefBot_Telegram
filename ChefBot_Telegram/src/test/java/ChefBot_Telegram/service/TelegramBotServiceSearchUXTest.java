package ChefBot_Telegram.service;

import ChefBot_Telegram.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelegramBotServiceSearchUXTest {

    private TelegramClient telegramClient;
    private GeminiService geminiService;
    private RecipeService recipeService;
    private TelegramBotService botService;

    private static final Long CHAT_ID = 999L;

    @BeforeEach
    void setUp() {
        telegramClient = Mockito.mock(TelegramClient.class);
        geminiService = Mockito.mock(GeminiService.class);
        recipeService = Mockito.mock(RecipeService.class);

        botService = new TelegramBotService("test-token", telegramClient, geminiService, recipeService);
    }

    private Recipe createRecipe(Long id, String title) {
        Recipe r = new Recipe();
        r.setId(id);
        r.setTitle(title);
        return r;
    }

    @Test
    @DisplayName("Deve exibir instruções detalhadas quando o argumento de busca estiver vazio")
    void shouldSendHelpWhenSearchArgsBlank() throws Exception {
        botService.handleSearchCommand(CHAT_ID, "");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(1)).execute(captor.capture());

        SendMessage sent = captor.getValue();
        assertTrue(sent.getText().contains("Como buscar no seu caderno"));
        assertTrue(sent.getText().contains("• *Por Nome:*"));
        assertTrue(sent.getText().contains("• *Por Ingrediente:*"));
    }

    @Test
    @DisplayName("Deve enviar apenas UMA mensagem compacta quando houver múltiplos resultados (evitando spam)")
    void shouldSendCompactListWhenMultipleResults() throws Exception {
        Recipe r1 = createRecipe(1L, "Bolo de Cenoura");
        Recipe r2 = createRecipe(2L, "Bolo de Fubá");

        when(recipeService.searchRecipes(CHAT_ID, "bolo")).thenReturn(List.of(r1, r2));
        when(recipeService.formatRecipeSummary(r1)).thenReturn("• `[#1]` *Bolo de Cenoura*");
        when(recipeService.formatRecipeSummary(r2)).thenReturn("• `[#2]` *Bolo de Fubá*");

        botService.handleSearchCommand(CHAT_ID, "bolo");

        // Verifica que apenas UMA mensagem foi enviada ao chat
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(1)).execute(captor.capture());

        SendMessage sent = captor.getValue();
        assertTrue(sent.getText().contains("Encontrei 2 receitas para \"bolo\":"));
        assertTrue(sent.getText().contains("[#1]"));
        assertTrue(sent.getText().contains("[#2]"));
        assertTrue(sent.getText().contains("/buscar <ID>"));
    }

    @Test
    @DisplayName("Deve enviar a ficha completa quando houver exatamente 1 resultado")
    void shouldSendFullRecipeWhenSingleResult() throws Exception {
        Recipe r1 = createRecipe(1L, "Bolo de Cenoura");
        when(recipeService.searchRecipes(CHAT_ID, "cenoura")).thenReturn(List.of(r1));
        when(recipeService.formatRecipeForTelegram(r1)).thenReturn("🍽️ *Bolo de Cenoura* (Ficha Completa)");

        botService.handleSearchCommand(CHAT_ID, "cenoura");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(1)).execute(captor.capture());

        SendMessage sent = captor.getValue();
        assertTrue(sent.getText().contains("(Ficha Completa)"));
    }

    @Test
    @DisplayName("Deve exibir dicas e receitas recentes quando nenhum resultado for encontrado")
    void shouldSendTipsAndRecentWhenZeroResults() throws Exception {
        when(recipeService.searchRecipes(CHAT_ID, "lasanha")).thenReturn(List.of());
        Recipe recent = createRecipe(5L, "Arroz de Forno");
        when(recipeService.findRecipesByChatId(CHAT_ID)).thenReturn(List.of(recent));
        when(recipeService.formatRecipeSummary(recent)).thenReturn("• `[#5]` *Arroz de Forno*");

        botService.handleSearchCommand(CHAT_ID, "lasanha");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(1)).execute(captor.capture());

        SendMessage sent = captor.getValue();
        assertTrue(sent.getText().contains("Nenhuma receita encontrada para"));
        assertTrue(sent.getText().contains("lasanha"));
        assertTrue(sent.getText().contains("Suas receitas recentes:"));
        assertTrue(sent.getText().contains("[#5]"));
    }
}
