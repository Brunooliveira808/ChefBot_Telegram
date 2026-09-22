package ChefBot_Telegram.model;

import jakarta.persistence.*;

@Entity
@Table(name = "instructions")
public class Instruction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "instruction_text", nullable = false, columnDefinition = "TEXT")
    private String instructionText;

    public Instruction() {}

    public Instruction(Long id, Recipe recipe, Integer stepNumber, String instructionText) {
        this.id = id;
        this.recipe = recipe;
        this.stepNumber = stepNumber;
        this.instructionText = instructionText;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Recipe getRecipe() { return recipe; }
    public void setRecipe(Recipe recipe) { this.recipe = recipe; }

    public Integer getStepNumber() { return stepNumber; }
    public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }

    public String getInstructionText() { return instructionText; }
    public void setInstructionText(String instructionText) { this.instructionText = instructionText; }
}
