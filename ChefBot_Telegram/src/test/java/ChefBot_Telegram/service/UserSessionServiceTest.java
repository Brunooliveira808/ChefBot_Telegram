package ChefBot_Telegram.service;

import ChefBot_Telegram.dto.RecipeResponseDTO;
import ChefBot_Telegram.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserSessionServiceTest {

    private UserSessionService userSessionService;
    private TelegramClient telegramClient;
    private GeminiService geminiService;
    private RecipeService recipeService;
    private TelegramBotService botService;

    private static final Long CHAT_ID = 777L;

    @BeforeEach
    void setUp() {
        userSessionService = new UserSessionService();
        telegramClient = Mockito.mock(TelegramClient.class);
        geminiService = Mockito.mock(GeminiService.class);
        recipeService = Mockito.mock(RecipeService.class);

        botService = new TelegramBotService("dummy-token", telegramClient, geminiService, recipeService, userSessionService);
    }

    @Test
    @DisplayName("Deve iniciar, recuperar e limpar uma sessão de usuário")
    void shouldManageSessionLifecycle() {
        userSessionService.startUpdateSession(CHAT_ID, 10L);

        Optional<UserSessionService.UserSession> session = userSessionService.getSession(CHAT_ID);
        assertTrue(session.isPresent());
        assertEquals(UserSessionService.SessionState.AWAITING_RECIPE_UPDATE, session.get().state());
        assertEquals(10L, session.get().targetRecipeId());

        userSessionService.clearSession(CHAT_ID);
        assertTrue(userSessionService.getSession(CHAT_ID).isEmpty());
    }

    @Test
    @DisplayName("Deve gerenciar fluxo interativo de atualização em 2 etapas")
    void shouldHandleTwoStepUpdateFlow() throws Exception {
        Recipe r = new Recipe();
        r.setId(1L);
        r.setTitle("Bolo de Fubá");
        when(recipeService.findRecipeByUserAndId(CHAT_ID, 1L)).thenReturn(Optional.of(r));

        // Etapa 1: Usuário digita "/atualizar 1" sem instruções
        botService.handleUpdateCommand(CHAT_ID, "1");

        // Sessão deve estar ativa
        assertTrue(userSessionService.getSession(CHAT_ID).isPresent());
        assertEquals(1L, userSessionService.getSession(CHAT_ID).get().targetRecipeId());

        ArgumentCaptor<SendMessage> captor1 = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(1)).execute(captor1.capture());
        assertTrue(captor1.getValue().getText().contains("Modo de edição ativado"));

        // Etapa 2: Usuário envia na mensagem seguinte "mudar o autor para Minha Tia"
        RecipeResponseDTO updatedDto = new RecipeResponseDTO(true, null, "Bolo de Fubá", null, "Minha Tia", null, null, null, null);
        Recipe updatedRecipe = new Recipe();
        updatedRecipe.setId(1L);
        updatedRecipe.setTitle("Bolo de Fubá");
        updatedRecipe.setSourceAuthor("Minha Tia");

        when(geminiService.updateRecipe(eq(r), eq("mudar o autor para Minha Tia"))).thenReturn(updatedDto);
        when(recipeService.updateRecipeFromDTO(eq(CHAT_ID), eq(1L), any(RecipeResponseDTO.class))).thenReturn(updatedRecipe);
        when(recipeService.formatRecipeForTelegram(updatedRecipe)).thenReturn("🍽️ *Bolo de Fubá* por Minha Tia");

        botService.applyRecipeUpdate(CHAT_ID, 1L, "mudar o autor para Minha Tia");

        ArgumentCaptor<SendMessage> captor2 = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeast(2)).execute(captor2.capture());

        SendMessage lastMessage = captor2.getAllValues().get(captor2.getAllValues().size() - 1);
        assertTrue(lastMessage.getText().contains("Receita atualizada com sucesso!"));
    }
}
