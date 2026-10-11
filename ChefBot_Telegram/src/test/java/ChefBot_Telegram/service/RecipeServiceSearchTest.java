package ChefBot_Telegram.service;

import ChefBot_Telegram.model.Ingredient;
import ChefBot_Telegram.model.Instruction;
import ChefBot_Telegram.model.Recipe;
import ChefBot_Telegram.repository.RecipeRepository;
import ChefBot_Telegram.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class RecipeServiceSearchTest {

    private RecipeRepository recipeRepository;
    private UserRepository userRepository;
    private RecipeService recipeService;

    private static final Long CHAT_ID = 123456L;

    @BeforeEach
    void setUp() {
        recipeRepository = Mockito.mock(RecipeRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        recipeService = new RecipeService(userRepository, recipeRepository);
    }

    private Recipe createSampleRecipe(Long id, String title, String category, String author, List<String> ingredients, List<String> instructions) {
        Recipe r = new Recipe();
        r.setId(id);
        r.setTitle(title);
        r.setCategory(category);
        r.setSourceAuthor(author);

        if (ingredients != null) {
            for (String ingName : ingredients) {
                Ingredient ing = new Ingredient();
                ing.setName(ingName);
                r.addIngredient(ing);
            }
        }

        if (instructions != null) {
            int step = 1;
            for (String text : instructions) {
                Instruction inst = new Instruction();
                inst.setStepNumber(step++);
                inst.setInstructionText(text);
                r.addInstruction(inst);
            }
        }
        return r;
    }

    @Test
    @DisplayName("Deve encontrar receitas com palavras fora de ordem (ex: 'cenoura bolo')")
    void shouldFindRecipeWithTokensOutOfOrder() {
        Recipe bolo = createSampleRecipe(1L, "Bolo fofinho de cenoura", "Sobremesa", "Vovó", List.of("Cenoura", "Trigo"), List.of("Misture tudo"));
        Recipe torta = createSampleRecipe(2L, "Torta de Frango", "Salgado", "Tia", List.of("Frango", "Massa"), List.of("Asse no forno"));

        when(recipeRepository.findByUserTelegramChatId(CHAT_ID)).thenReturn(List.of(bolo, torta));

        List<Recipe> results = recipeService.searchRecipes(CHAT_ID, "cenoura bolo");

        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
        assertEquals("Bolo fofinho de cenoura", results.get(0).getTitle());
    }

    @Test
    @DisplayName("Deve ignorar stopwords como 'de', 'com', 'do'")
    void shouldFindRecipeIgnoringStopwords() {
        Recipe bolo = createSampleRecipe(1L, "Bolo de Cenoura", "Sobremesa", null, List.of("Cenoura"), List.of("Asse"));
        when(recipeRepository.findByUserTelegramChatId(CHAT_ID)).thenReturn(List.of(bolo));

        List<Recipe> results = recipeService.searchRecipes(CHAT_ID, "bolo com cenoura");

        assertFalse(results.isEmpty());
        assertEquals("Bolo de Cenoura", results.get(0).getTitle());
    }

    @Test
    @DisplayName("Deve encontrar receita por ingrediente")
    void shouldFindRecipeByIngredient() {
        Recipe boloChocolate = createSampleRecipe(1L, "Bolo Simples", "Doces", null, List.of("Chocolate em pó", "Leite"), List.of("Misture"));
        Recipe salada = createSampleRecipe(2L, "Salada Verde", "Fit", null, List.of("Alface", "Tomate"), List.of("Lave"));

        when(recipeRepository.findByUserTelegramChatId(CHAT_ID)).thenReturn(List.of(boloChocolate, salada));

        List<Recipe> results = recipeService.searchRecipes(CHAT_ID, "chocolate");

        assertEquals(1, results.size());
        assertEquals("Bolo Simples", results.get(0).getTitle());
    }

    @Test
    @DisplayName("Deve encontrar receita pelo texto do modo de preparo")
    void shouldFindRecipeByInstructionText() {
        Recipe batata = createSampleRecipe(1L, "Batata Rústica", "Acompanhamento", null, List.of("Batata"), List.of("Coloque na airfryer por 20 minutos"));
        when(recipeRepository.findByUserTelegramChatId(CHAT_ID)).thenReturn(List.of(batata));

        List<Recipe> results = recipeService.searchRecipes(CHAT_ID, "airfryer");

        assertEquals(1, results.size());
        assertEquals("Batata Rústica", results.get(0).getTitle());
    }

    @Test
    @DisplayName("Deve priorizar busca por ID numérico e incluir títulos com número")
    void shouldPrioritizeExactIdAndIncludeMatchingText() {
        Recipe r4 = createSampleRecipe(4L, "Salada Caesar", "Entrada", null, List.of("Alface"), List.of("Sirva"));
        Recipe pizza = createSampleRecipe(10L, "Pizza 4 queijos especial", "Prato Principal", null, List.of("Queijo"), List.of("Asse"));

        when(recipeRepository.findByUserTelegramChatId(CHAT_ID)).thenReturn(List.of(pizza, r4));

        List<Recipe> results = recipeService.searchRecipes(CHAT_ID, "4");

        assertEquals(2, results.size());
        assertEquals(4L, results.get(0).getId()); // ID 4 tem prioridade máxima
        assertEquals(10L, results.get(1).getId()); // Pizza 4 queijos também encontrada
    }

    @Test
    @DisplayName("Deve ser insensível a acentos (ex: 'macarrao' acha 'Macarrão')")
    void shouldFindRecipeWithoutAccents() {
        Recipe macarrao = createSampleRecipe(1L, "Macarrão ao alho e óleo", "Massa", null, List.of("Massa"), List.of("Cozinhe"));
        when(recipeRepository.findByUserTelegramChatId(CHAT_ID)).thenReturn(List.of(macarrao));

        List<Recipe> results = recipeService.searchRecipes(CHAT_ID, "macarrao");

        assertEquals(1, results.size());
        assertEquals("Macarrão ao alho e óleo", results.get(0).getTitle());
    }

    @Test
    @DisplayName("Deve tratar plural e singular comum (ex: 'ovos' acha 'ovo')")
    void shouldHandlePluralAndSingular() {
        Recipe omelete = createSampleRecipe(1L, "Omelete Rápido", "Lanche", null, List.of("Ovo caipira", "Sal"), List.of("Frite"));
        when(recipeRepository.findByUserTelegramChatId(CHAT_ID)).thenReturn(List.of(omelete));

        List<Recipe> results = recipeService.searchRecipes(CHAT_ID, "ovos");

        assertEquals(1, results.size());
        assertEquals("Omelete Rápido", results.get(0).getTitle());
    }
}
