package ChefBot_Telegram.repository;

import ChefBot_Telegram.model.Instruction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstructionRepository extends JpaRepository<Instruction, Long> {
    List<Instruction> findByRecipeIdOrderByStepNumberAsc(Long recipeId);
}
