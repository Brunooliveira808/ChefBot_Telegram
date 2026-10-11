# 🎯 TASK 05: Lógica de Atualização com IA e Prompt de Edição

## 📌 Contexto e Problema
O método `GeminiService.updateRecipe(Recipe existingRecipe, String updateInstructions)` envia a receita inteira serializada em JSON para o modelo Gemini e solicita um novo JSON completo com base nas instruções de alteração do usuário.

No entanto, o prompt `UPDATE_SYSTEM_PROMPT` apresenta fraquezas que causam problemas na prática:
1. **Alucinação e Modificação Espúria:**
   Ao receber a receita inteira, o modelo de linguagem muitas vezes:
   - "Corrige" ou altera nomes de ingredientes que o usuário não pediu para mudar (ex: troca "açúcar" por "açúcar refinado" ou "xícara" por "gramas").
   - Resume ou reescreve as etapas de modo de preparo, perdendo detalhes dados pela família/autor original.
   - Retorna apenas o ingrediente que foi modificado, omitindo o restante da lista e enganando o parser.
2. **Falta de Edições Rápidas/Determinísticas sem IA:**
   Muitas alterações feitas pelos usuários são extremamente simples e diretas, por exemplo:
   - *"Mudar autor para Minha Mãe"*
   - *"Tempo de preparo: 40 minutos"*
   - *"Rendimento: 6 porções"*
   - *"Mudar título para Bolo de Chocolate"*
   Chamar uma API externa de IA para alterar apenas uma string de autor ou um número de tempo gasta cota de API, adiciona latência desnecessária de 3 a 5 segundos e pode falhar se a API Gemini estiver indisponível.

---

## 🎯 Objetivos da Task
1. Otimizar e blindar o prompt do sistema `UPDATE_SYSTEM_PROMPT` em `GeminiService.java`:
   - Enfatizar com severidade que campos não mencionados pelo usuário DEVEM ser copiados caractere por caractere sem qualquer modificação.
   - Proibir sumarização de etapas e conversão de unidades.
2. Implementar um mecanismo híbrido / atalho determinístico:
   - Se a instrução do usuário for uma alteração pontual simples e direta (ex: "tempo: 45 min", "autor: X", "título: Y"), aplicar a alteração diretamente sem precisar chamar a IA, garantindo 100% de precisão e resposta instantânea.
   - Se for uma alteração complexa (ex: "troque o trigo por farinha de amêndoas e ajuste o modo de preparo"), direcionar para a IA Gemini com o prompt blindado.
3. Criar testes unitários em `GeminiServiceUpdatePromptTest.java`.

---

## 📂 Arquivos Envolvidos
- `ChefBot_Telegram/src/main/java/ChefBot_Telegram/service/GeminiService.java`
- `ChefBot_Telegram/src/test/java/ChefBot_Telegram/service/GeminiServiceUpdatePromptTest.java` (criar)

---

## 🔨 Instruções Passo a Passo para a LLM

### Passo 1: Blindar o `UPDATE_SYSTEM_PROMPT` em `GeminiService.java`
Substitua o prompt de atualização para o seguinte formato estrito:
```java
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
```

### Passo 2: Implementar Atalho Determinístico (Fast-Path)
Antes de disparar a requisição HTTP para a Gemini API, analise se a instrução de alteração pode ser resolvida sem IA:
```java
public RecipeResponseDTO updateRecipe(Recipe existingRecipe, String updateInstructions) {
    // 1. Tentar Fast-Path para edições pontuais simples
    Optional<RecipeResponseDTO> directEdit = tryDirectFieldEdit(existingRecipe, updateInstructions);
    if (directEdit.isPresent()) {
        log.info("Edição resolvida via Fast-Path sem chamada externa de IA.");
        return directEdit.get();
    }

    // 2. Caso contrário, aciona o modelo Gemini com o prompt blindado
    ...
```

Implemente o helper `tryDirectFieldEdit`:
- Se a instrução corresponder a regex simples como:
  - `(?i)^mudar?\s+o?\s*autor\s+para\s+(.+)` -> atualiza apenas `sourceAuthor`.
  - `(?i)^mudar?\s+o?\s*t[íi]tulo\s+para\s+(.+)` -> atualiza apenas `title`.
  - `(?i)^tempo\s*(?:de\s+preparo)?\s*(?:é|de|=|:)\s*(\d+)\s*(?:min|minutos)?` -> atualiza apenas `prepTimeMinutes`.
  - `(?i)^rendimento\s*(?:é|=|:)\s*(\d+)\s*(?:por[çc][õo]es)?` -> atualiza apenas `portions`.
- Retorna um novo `RecipeResponseDTO` clonando todos os ingredientes e passos originais intactos e modificando apenas o campo pontual.

### Passo 3: Criar Testes em `GeminiServiceUpdatePromptTest.java`
Criar testes unitários para validar:
1. Fast-path de alteração de autor sem chamar IA.
2. Fast-path de alteração de tempo de preparo sem chamar IA.
3. Chamada com mock da Gemini API para edições que envolvem troca de ingredientes.

---

## 🧪 Como Testar e Ter Certeza que Funciona
Execute:
```bash
./gradlew test --tests "ChefBot_Telegram.service.GeminiServiceUpdatePromptTest"
```

### Critério de Sucesso:
- Testes passam com `BUILD SUCCESSFUL`.
- Edições simples de autor/tempo são instantâneas e não dependem de rede externa.
- Edições complexas de ingredientes preservam a estrutura JSON completa.
