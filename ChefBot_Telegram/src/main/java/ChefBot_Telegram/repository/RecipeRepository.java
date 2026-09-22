package ChefBot_Telegram.repository;

import ChefBot_Telegram.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    List<Recipe> findByUserTelegramChatId(Long telegramChatId);
    List<Recipe> findByUserTelegramChatIdAndTitleContainingIgnoreCase(Long telegramChatId, String title);
}
