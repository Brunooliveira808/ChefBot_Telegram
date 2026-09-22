package ChefBot_Telegram.service;

import ChefBot_Telegram.dto.RecipeResponseDTO;
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

    private static final String GEMINI_API_URL = 
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    private static final String SYSTEM_PROMPT = """
        Você é um assistente culinário especialista em extrair e estruturar receitas a partir de textos e conversas humanas informais.
        
        DIRETRIZES DE EXTRAÇÃO:
        1. SEJA EXTREMAMENTE FLEXÍVEL: O usuário pode mandar um texto incompleto, curto, bagunçado ou informal (ex: "arroz. Preparo: amassar 2 alhos, botar sal e etc").
        2. DEDUÇÃO DE INGREDIENTES: Se os ingredientes estiverem misturados dentro do texto de preparo ou falados soltos, identifique cada um deles e liste na array "ingredients".
        3. QUANTIDADES AUSENTES: Se o usuário não informar quantidades ou unidades, coloque quantity="a gosto" ou "1" e unit=null.
        4. CAMPOS FALTANTES: Se não houver categoria, tempo de preparo (prepTimeMinutes), porções (portions) ou autor (sourceAuthor), preencha com null. NUNCA recuse uma receita por falta desses dados.
        5. TÍTULO: Se o usuário não der um título claro, deduza um título coerente (ex: "Arroz Simples", "Molho da Vovó", etc.).
        6. PASSOS: Divida a explicação em passos claros numerados na array "instructions".
        
        FORMATO OBRIGATÓRIO DO JSON:
        {
          "title": "Título da Receita",
          "category": "Almoço / Jantar / Sobremesa / Molho / etc ou null",
          "sourceAuthor": "Quem ensinou ou null",
          "prepTimeMinutes": null,
          "portions": null,
          "ingredients": [
            {
              "name": "Nome do ingrediente",
              "quantity": "2 / 1 xícara / a gosto",
              "unit": "unidades / colheres / dentes / null",
              "notes": "picado / batido / null"
            }
          ],
          "instructions": [
            {
              "stepNumber": 1,
              "description": "Texto do passo"
            }
          ]
        }
        
        Responda APENAS com o objeto JSON válido, sem texto antes ou depois.
        """;

    public GeminiService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Extrai os dados da receita a partir de um texto digitado no Telegram.
     */
    public RecipeResponseDTO extractRecipeFromText(String userText) {
        try {
            log.info("Processando texto de receita recebido: {}", userText);
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

            return callGeminiApi(payload);
        } catch (Exception e) {
            log.error("Erro ao processar receita por texto no Gemini: {}", e.getMessage(), e);
            throw new RuntimeException("Não foi possível extrair a receita do texto fornecido.", e);
        }
    }

    /**
     * Extrai os dados da receita a partir de um arquivo de áudio recebido pelo Telegram.
     */
    public RecipeResponseDTO extractRecipeFromAudio(byte[] audioBytes, String mimeType) {
        try {
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

            return callGeminiApi(payload);
        } catch (Exception e) {
            log.error("Erro ao processar áudio de receita no Gemini: {}", e.getMessage(), e);
            throw new RuntimeException("Não foi possível extrair a receita do áudio fornecido.", e);
        }
    }

    private RecipeResponseDTO callGeminiApi(Map<String, Object> payload) throws Exception {
        String responseBody = restClient.post()
            .uri(GEMINI_API_URL + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .body(String.class);

        log.debug("Resposta da API Gemini: {}", responseBody);

        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty()) {
            throw new RuntimeException("O Gemini não retornou nenhuma resposta válida.");
        }

        String jsonContent = candidates.get(0)
            .path("content")
            .path("parts")
            .get(0)
            .path("text")
            .asText()
            .trim();

        // Limpeza de possíveis marcadores markdown ```json ... ```
        if (jsonContent.startsWith("```json")) {
            jsonContent = jsonContent.substring(7);
        } else if (jsonContent.startsWith("```")) {
            jsonContent = jsonContent.substring(3);
        }
        if (jsonContent.endsWith("```")) {
            jsonContent = jsonContent.substring(0, jsonContent.length() - 3);
        }
        jsonContent = jsonContent.trim();

        log.info("JSON estruturado pela IA: {}", jsonContent);

        return objectMapper.readValue(jsonContent, RecipeResponseDTO.class);
    }
}
