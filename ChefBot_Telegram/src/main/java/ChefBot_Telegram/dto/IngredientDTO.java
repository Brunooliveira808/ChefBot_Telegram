package ChefBot_Telegram.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;

public record IngredientDTO(
    String name,
    @JsonDeserialize(using = StringOrNumberDeserializer.class)
    String quantity,
    String unit,
    String notes
) {
    public static class StringOrNumberDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return p.getText();
        }
    }
}
