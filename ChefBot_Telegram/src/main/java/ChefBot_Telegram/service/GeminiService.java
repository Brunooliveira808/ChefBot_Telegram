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

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    // Modelos oficiais do Google AI Studio com fallback de alta disponibilidade
    private static final List<String> MODELS = List.of(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash"
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
        Você é um assistente culinário especialista em editar e atualizar receitas já cadastradas.
        
        Você receberá:
        1. Os dados ATUAIS da receita em formato JSON.
        2. O PEDIDO DE ALTERAÇÃO do usuário (ex: "mude o título para X", "troque o ingrediente Y por Z", "o autor é minha vó", "acrescente 1 colher de sal", "o tempo de preparo é 45 minutos").
        
        REGRAS DE ATUALIZAÇÃO:
        - Aplique as alterações solicitadas pelo usuário com exatidão.
        - Mantenha todos os outros campos, ingredientes e passos que NÃO foram solicitados para alteração exatamente como estavam.
        - Defina "isRecipe": true e "refusalReason": null.
        
        FORMATO OBRIGATÓRIO DO JSON:
        {
          "isRecipe": true,
          "refusalReason": null,
          "title": "Título atualizado ou mantido",
          "category": "Categoria atualizada ou mantida",
          "sourceAuthor": "Autor atualizado ou mantido",
          "prepTimeMinutes": 30,
          "portions": 4,
          "ingredients": [
            {
              "name": "Nome do ingrediente",
              "quantity": "quantidade",
              "unit": "unidade ou null",
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
        
        Responda APENAS com o objeto JSON válido.
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
     * Atualiza uma receita existente com base nas instruções fornecidas pelo usuário.
     */
    public RecipeResponseDTO updateRecipe(Recipe existingRecipe, String updateInstructions) {
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

        for (String model : MODELS) {
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

