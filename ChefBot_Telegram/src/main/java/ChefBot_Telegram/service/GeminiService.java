package ChefBot_Telegram.service;

import ChefBot_Telegram.dto.IngredientDTO;
import ChefBot_Telegram.dto.InstructionDTO;
import ChefBot_Telegram.dto.RecipeResponseDTO;
import ChefBot_Telegram.model.Ingredient;
import ChefBot_Telegram.model.Instruction;
import ChefBot_Telegram.model.Recipe;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model.primary:gemini-3.1-flash-lite}")
    private String primaryModel;

    // Modelos ativos suportados pela API Generative Language do Google
    private static final List<String> FALLBACK_MODELS = List.of(
        "gemini-3.1-flash-lite",
        "gemini-flash-lite-latest",
        "gemini-3.5-flash"
    );

    private static final String SYSTEM_PROMPT = """
        Você é um assistente culinário especialista em extrair e estruturar receitas a partir de textos e conversas humanas informais.
        
        REQUISITOS OBRIGATÓRIOS:
        
        1. VALIDAÇÃO DE RECEITA (MUITO IMPORTANTE):
        - Avalie se o conteúdo fornecido pelo usuário REALMENTE se refere a uma receita culinária, modo de preparo de prato/bebida ou lista de ingredientes.
        - Se a mensagem for apenas conversa cotidiana, saudação (ex: "Oi", "Tudo bem?", "Boa tarde"), piada, pergunta aleatória ou texto sem contexto culinário:
          Retorne "isRecipe": false, "refusalReason": "Mensagem não contém uma receita culinária.", "title": null, "ingredients": [], "instructions": [].
        - Se contiver uma receita (mesmo curta, simples ou falada informalmente), retorne "isRecipe": true.

        2. PRESERVAÇÃO EXATA DO TÍTULO (CRÍTICO):
        - Se o usuário forneceu um nome ou título (ex: "Bolo de Cenoura da Mamãe", "Arroz de forno", "Miojo com requeijão"), use EXATAMENTE o nome dito pelo usuário. NÃO altere, não invente subtítulos, não "embeleze" e não troque por nomes genéricos.
        - Somente se o usuário NÃO tiver mencionado nenhum nome ou prato em nenhum momento, deduza um título simples baseado no prato principal.

        3. INGREDIENTES E QUANTIDADES:
        - Identifique todos os ingredientes mencionados.
        - Se houver quantidade/unidade, preencha quantity e unit. Se não houver, coloque quantity="a gosto" ou "1" e unit=null.
        
        4. PASSOS:
        - Divida o modo de preparo em passos claros e ordenados em "instructions".

        FORMATO OBRIGATÓRIO DO JSON:
        {
          "isRecipe": true,
          "refusalReason": null,
          "title": "Título Exato Informado pelo Usuário",
          "category": "Sobremesa / Almoço / Jantar / Bebida / null",
          "sourceAuthor": "Quem ensinou ou null",
          "prepTimeMinutes": null,
          "portions": null,
          "ingredients": [
            {
              "name": "Nome do ingrediente",
              "quantity": "2",
              "unit": "xícaras",
              "notes": null
            }
          ],
          "instructions": [
            {
              "stepNumber": 1,
              "description": "Texto do passo"
            }
          ]
        }
        
        Responda APENAS com o objeto JSON válido, sem qualquer texto adicional antes ou depois.
        """;

    private static final String UPDATE_SYSTEM_PROMPT = """
        Você é um assistente culinário especialista em editar receitas já cadastradas.
        
        Você receberá:
        1. Os dados ATUAIS da receita em formato JSON.
        2. O PEDIDO DE ALTERAÇÃO do usuário.
        
        REGRAS INEGOCIÁVEIS:
        1. IMUTABILIDADE: Você NÃO DEVE alterar, resumir, traduzir ou reescrever NENHUM ingrediente ou instrução que o usuário não tenha pedido explicitamente para mudar.
        2. PRESERVAÇÃO DE LISTAS: Se o usuário pedir para adicionar um ingrediente, adicione-o ao final da lista de ingredientes existente, mantendo todos os anteriores intactos.
        3. SUBSTITUIÇÃO: Se o usuário pedir para trocar um ingrediente (ex: "troque açúcar por adoçante"), substitua apenas o ingrediente especificado mantendo a posição e preserve todos os outros.
        4. INTEGRIDADE DOS PASSOS: Mantenha a redação original do modo de preparo, a menos que o usuário peça uma alteração explícita nos passos.
        5. RESPOSTA: Responda APENAS com o objeto JSON final completo atualizado.
        
        FORMATO OBRIGATÓRIO DO JSON:
        {
          "isRecipe": true,
          "refusalReason": null,
          "title": "Título",
          "category": "Categoria ou null",
          "sourceAuthor": "Autor ou null",
          "prepTimeMinutes": 30,
          "portions": 4,
          "ingredients": [
            {
              "name": "Nome",
              "quantity": "2",
              "unit": "xícaras",
              "notes": null
            }
          ],
          "instructions": [
            {
              "stepNumber": 1,
              "description": "Texto do passo"
            }
          ]
        }
        """;

    public GeminiService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Extrai os dados da receita a partir de um texto digitado no Telegram.
     */
    public RecipeResponseDTO extractRecipeFromText(String userText) {
        log.info("Processando texto recebido: {}", userText);
        Map<String, Object> payload = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", SYSTEM_PROMPT + "\n\nEntrada do usuário:\n\"" + userText + "\"")
                ))
            ),
            "generationConfig", Map.of(
                "responseMimeType", "application/json"
            )
        );

        return callGeminiWithFallback(payload);
    }

    /**
     * Extrai os dados da receita a partir de um arquivo de áudio recebido pelo Telegram.
     */
    public RecipeResponseDTO extractRecipeFromAudio(byte[] audioBytes, String mimeType) {
        String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

        Map<String, Object> payload = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("inlineData", Map.of(
                        "mimeType", mimeType != null ? mimeType : "audio/ogg",
                        "data", base64Audio
                    )),
                    Map.of("text", SYSTEM_PROMPT + "\n\nExtraia a receita deste áudio gravado.")
                ))
            ),
            "generationConfig", Map.of(
                "responseMimeType", "application/json"
            )
        );

        return callGeminiWithFallback(payload);
    }

    /**
     * Tenta aplicar alterações simples e determinísticas de campos sem precisar chamar a API do Gemini.
     */
    Optional<RecipeResponseDTO> tryDirectFieldEdit(Recipe existingRecipe, String updateInstructions) {
        if (updateInstructions == null || updateInstructions.isBlank()) {
            return Optional.empty();
        }

        String trimmed = updateInstructions.trim();
        List<IngredientDTO> ingredientsDTO = existingRecipe.getIngredients().stream()
            .map(i -> new IngredientDTO(i.getName(), i.getQuantity(), i.getUnit(), i.getNotes()))
            .toList();

        List<InstructionDTO> instructionsDTO = existingRecipe.getInstructions().stream()
            .map(inst -> new InstructionDTO(inst.getStepNumber(), inst.getInstructionText()))
            .toList();

        // 1. Alterar Autor
        java.util.regex.Matcher authorMatcher = java.util.regex.Pattern
            .compile("(?i)^(?:mudar?\\s+(?:o\\s+)?)?autor\\s*(?:para|pra|=|:|[eé])\\s*(.+)$")
            .matcher(trimmed);
        if (authorMatcher.matches()) {
            String newAuthor = authorMatcher.group(1).trim();
            return Optional.of(new RecipeResponseDTO(
                true, null, existingRecipe.getTitle(), existingRecipe.getCategory(),
                newAuthor, existingRecipe.getPrepTimeMinutes(), existingRecipe.getPortions(),
                ingredientsDTO, instructionsDTO
            ));
        }

        // 2. Alterar Título
        java.util.regex.Matcher titleMatcher = java.util.regex.Pattern
            .compile("(?i)^(?:mudar?\\s+(?:o\\s+)?)?t[íi]tulo\\s*(?:para|pra|=|:|[eé])\\s*(.+)$")
            .matcher(trimmed);
        if (titleMatcher.matches()) {
            String newTitle = titleMatcher.group(1).trim();
            return Optional.of(new RecipeResponseDTO(
                true, null, newTitle, existingRecipe.getCategory(),
                existingRecipe.getSourceAuthor(), existingRecipe.getPrepTimeMinutes(), existingRecipe.getPortions(),
                ingredientsDTO, instructionsDTO
            ));
        }

        // 3. Alterar Tempo de Preparo
        java.util.regex.Matcher timeMatcher = java.util.regex.Pattern
            .compile("(?i)^(?:mudar?\\s+(?:o\\s+)?)?tempo(?:\\s+de\\s+preparo)?\\s*(?:para|pra|=|:|[eé]|de)?\\s*(\\d+)(?:\\s*(?:min|minutos))?$")
            .matcher(trimmed);
        if (timeMatcher.matches()) {
            int newTime = Integer.parseInt(timeMatcher.group(1));
            return Optional.of(new RecipeResponseDTO(
                true, null, existingRecipe.getTitle(), existingRecipe.getCategory(),
                existingRecipe.getSourceAuthor(), newTime, existingRecipe.getPortions(),
                ingredientsDTO, instructionsDTO
            ));
        }

        // 4. Alterar Rendimento / Porções
        java.util.regex.Matcher portionsMatcher = java.util.regex.Pattern
            .compile("(?i)^(?:mudar?\\s+(?:o\\s+)?)?(?:rendimento|por[çc][õo]es)\\s*(?:para|pra|=|:|[eé]|de)?\\s*(\\d+)(?:\\s*por[çc][õo]es)?$")
            .matcher(trimmed);
        if (portionsMatcher.matches()) {
            int newPortions = Integer.parseInt(portionsMatcher.group(1));
            return Optional.of(new RecipeResponseDTO(
                true, null, existingRecipe.getTitle(), existingRecipe.getCategory(),
                existingRecipe.getSourceAuthor(), existingRecipe.getPrepTimeMinutes(), newPortions,
                ingredientsDTO, instructionsDTO
            ));
        }

        return Optional.empty();
    }

    /**
     * Atualiza uma receita existente com base nas instruções fornecidas pelo usuário.
     */
    public RecipeResponseDTO updateRecipe(Recipe existingRecipe, String updateInstructions) {
        // Tentar Fast-Path para edições pontuais simples sem gastar IA
        Optional<RecipeResponseDTO> directEdit = tryDirectFieldEdit(existingRecipe, updateInstructions);
        if (directEdit.isPresent()) {
            log.info("Edição pontual da receita aplicada diretamente (Fast-Path sem chamada externa de IA).");
            return directEdit.get();
        }

        try {
            List<IngredientDTO> ingredientsDTO = existingRecipe.getIngredients().stream()
                .map(i -> new IngredientDTO(i.getName(), i.getQuantity(), i.getUnit(), i.getNotes()))
                .toList();

            List<InstructionDTO> instructionsDTO = existingRecipe.getInstructions().stream()
                .map(inst -> new InstructionDTO(inst.getStepNumber(), inst.getInstructionText()))
                .toList();

            RecipeResponseDTO currentDto = new RecipeResponseDTO(
                true,
                null,
                existingRecipe.getTitle(),
                existingRecipe.getCategory(),
                existingRecipe.getSourceAuthor(),
                existingRecipe.getPrepTimeMinutes(),
                existingRecipe.getPortions(),
                ingredientsDTO,
                instructionsDTO
            );

            String currentJson = objectMapper.writeValueAsString(currentDto);

            String prompt = UPDATE_SYSTEM_PROMPT + "\n\nRECEITA ATUAL (JSON):\n" + currentJson + 
                            "\n\nPEDIDO DE ALTERAÇÃO DO USUÁRIO:\n\"" + updateInstructions + "\"";

            Map<String, Object> payload = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(
                        Map.of("text", prompt)
                    ))
                ),
                "generationConfig", Map.of(
                    "responseMimeType", "application/json"
                )
            );

            return callGeminiWithFallback(payload);
        } catch (Exception e) {
            log.error("Erro ao preparar atualização da receita no Gemini: {}", e.getMessage(), e);
            throw new RuntimeException("Não foi possível atualizar a receita.", e);
        }
    }

    private RecipeResponseDTO callGeminiWithFallback(Map<String, Object> payload) {
        Exception lastException = null;

        List<String> modelsToTry = new java.util.ArrayList<>();
        if (primaryModel != null && !primaryModel.isBlank()) {
            modelsToTry.add(primaryModel.trim());
        }
        for (String m : FALLBACK_MODELS) {
            if (!modelsToTry.contains(m)) {
                modelsToTry.add(m);
            }
        }

        for (String model : modelsToTry) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
            try {
                log.info("Tentando chamar modelo Gemini: {}", model);
                return executeApiCall(url, payload);
            } catch (Exception e) {
                log.warn("Modelo {} falhou ({}). Tentando próximo modelo...", model, e.getMessage());
                lastException = e;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }
        }

        log.error("Todos os modelos Gemini falharam.", lastException);
        throw new RuntimeException("Não foi possível processar com a IA no momento. Tente novamente.", lastException);
    }

    RecipeResponseDTO parseGeminiResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty()) {
            throw new RuntimeException("O Gemini não retornou nenhuma resposta válida.");
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        String jsonContent = null;
        for (JsonNode part : parts) {
            if (part.has("text") && !part.path("text").asText().isBlank()) {
                jsonContent = part.path("text").asText().trim();
                break;
            }
        }

        if (jsonContent == null) {
            throw new RuntimeException("Nenhum texto JSON foi encontrado na resposta do Gemini.");
        }

        String cleanedJson = cleanJsonContent(jsonContent);
        log.info("JSON estruturado pela IA com sucesso: {}", cleanedJson);

        return objectMapper.readValue(cleanedJson, RecipeResponseDTO.class);
    }

    static String cleanJsonContent(String jsonContent) {
        if (jsonContent == null) return "";
        String cleaned = jsonContent.trim();
        // Limpeza de possíveis marcadores markdown ```json ... ```
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private RecipeResponseDTO executeApiCall(String url, Map<String, Object> payload) throws Exception {
        String responseBody = restClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .body(String.class);

        return parseGeminiResponse(responseBody);
    }
}

