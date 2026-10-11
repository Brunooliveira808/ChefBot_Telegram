package ChefBot_Telegram.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserSessionService {

    public enum SessionState {
        IDLE,
        AWAITING_RECIPE_UPDATE
    }

    public record UserSession(SessionState state, Long targetRecipeId) {}

    private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();

    public void startUpdateSession(Long chatId, Long recipeId) {
        sessions.put(chatId, new UserSession(SessionState.AWAITING_RECIPE_UPDATE, recipeId));
    }

    public Optional<UserSession> getSession(Long chatId) {
        return Optional.ofNullable(sessions.get(chatId));
    }

    public void clearSession(Long chatId) {
        sessions.remove(chatId);
    }
}
