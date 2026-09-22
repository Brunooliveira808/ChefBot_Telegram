package ChefBot_Telegram.repository;

import ChefBot_Telegram.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTelegramChatId(Long telegramChatId);
}
