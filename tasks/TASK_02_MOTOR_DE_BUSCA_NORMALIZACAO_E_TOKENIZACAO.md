# 🎯 TASK 02: Problemas ao Buscar Receitas — Motor de Busca e Tokenização

## 📌 Contexto e Problema
O método `RecipeService.searchRecipes(Long telegramChatId, String query)` apresenta falhas severas que impedem o usuário de encontrar suas receitas:

1. **Busca por substring exata (falha em múltiplas palavras):**
   O código atual faz `normalizeText(r.getTitle()).contains(normalizedQuery)`.
   Se o usuário digita:
   - `"bolo cenoura"` e o título é `"Bolo de Cenoura"`, o método retorna **FALSO**, porque a substring `"bolo cenoura"` não existe no texto original!
   - Se o usuário digita `"cenoura bolo"`, também não encontra.
2. **Stopwords em português:**
   Termos de ligação como `"de"`, `"com"`, `"da"`, `"do"`, `"para"`, `"e"` quebram a correspondência exata de substrings.
   Exemplo: buscar `"torta de queijo"` não acha uma receita cadastrada como `"Torta Queijo Rápida"`.
3. **Colisão de busca numérica:**
   Se o usuário busca pelo número `"4"` (ex: querendo "Pizza 4 queijos"), a linha `if (numeric.matches("^\\d+$"))` intercepta a busca, procura a receita que tem ID 4, e retorna **apenas ela imediatamente**, ignorando todas as receitas que possuem "4" no nome ou ingredientes!
4. **Modo de preparo ignorado:**
   A busca só olha título, autor, categoria e ingredientes. Se o usuário procura "airfryer", "forno", "liquidificador" ou "geladeira", a busca falha pois o campo `instructionText` não é pesquisado.
5. **Tratamento de Singular/Plural simples:**
   Buscar `"ovos"` não encontra receitas cadastradas com ingrediente `"ovo"`.

---

## 🎯 Objetivos da Task
1. Refatorar `RecipeService.searchRecipes` para implementar um algoritmo de busca por relevância e tokens:
   - Tokenizar a query do usuário em palavras individuais.
   - Ignorar stopwords comuns em português (`de`, `da`, `do`, `com`, `para`, `em`, `e`, `a`, `o`, `um`, `uma`).
   - Normalizar caracteres (remover acentos, pontuação, converter para minúsculas).
2. Estender a busca para cobrir:
   - Título da receita (peso de relevância maior).
   - Ingredientes (nome e observações).
   - Categoria e autor.
   - Instruções de preparo (`instructionText`).
3. Corrigir a busca por ID numérico:
   - Se o ID numérico existir, a receita correspondente deve ser adicionada aos resultados prioritariamente (topo da lista), mas se houver receitas com o número no título (ex: "4 Queijos"), elas também devem ser incluídas.
4. Criar testes unitários exaustivos em `RecipeServiceSearchTest.java`.

---

## 📂 Arquivos Envolvidos
- `ChefBot_Telegram/src/main/java/ChefBot_Telegram/service/RecipeService.java`
- `ChefBot_Telegram/src/test/java/ChefBot_Telegram/service/RecipeServiceSearchTest.java` (criar)

---

## 🔨 Instruções Passo a Passo para a LLM

### Passo 1: Adicionar lista de Stopwords e Helper de Tokenização em `RecipeService.java`
Defina as stopwords ignoradas na busca:
```java
private static final Set<String> STOPWORDS = Set.of(
    "de", "da", "do", "das", "dos", "com", "sem", "para", "em", "e", "ou", "a", "o", "as", "os", "um", "uma", "uns", "umas"
);
```

Crie um método que divide o texto em termos limpos:
```java
List<String> extractSearchTokens(String query) {
    if (query == null) return List.of();
    String normalized = normalizeText(query);
    String[] words = normalized.split("[^a-z0-9]+");
    List<String> tokens = new ArrayList<>();
    for (String w : words) {
        if (!w.isBlank() && !STOPWORDS.contains(w)) {
            tokens.add(w);
        }
    }
    return tokens.isEmpty() && !normalized.isBlank() ? List.of(normalized.trim()) : tokens;
}
```

### Passo 2: Implementar Cálculo de Relevância / Score
Para cada receita do usuário, calcular uma pontuação baseada na quantidade de tokens encontrados:
- **Título:** Cada token encontrado soma **10 pontos**. Se o título contiver todos os tokens, adiciona bônus de **20 pontos**.
- **Categoria ou Autor:** Cada token soma **5 pontos**.
- **Ingredientes (Nome/Notas):** Cada token soma **4 pontos**.
- **Instruções (Modo de preparo):** Cada token soma **2 pontos**.

Adicionalmente, se a query original for puramente numérica (ex: `"1"` ou `"#1"`):
- Se `recipe.getId().equals(idBuscado)`, atribui **100 pontos** de prioridade máxima.

Receitas com pontuação maior que 0 entram no resultado, ordenadas pela pontuação decrescente (`sort by score desc`).

### Passo 3: Implementar tratamento básico de Plural em Português
Caso uma palavra termine em `"s"` e tenha mais de 3 letras (ex: `"ovos"` -> `"ovo"`, `"cenouras"` -> `"cenoura"`), o token sem o `"s"` final também deve pontuar se o token original não encontrar correspondência exata.

### Passo 4: Criar Testes Unitários em `RecipeServiceSearchTest.java`
Crie casos de teste cobrindo:
1. **Busca com palavras fora de ordem:** `"cenoura bolo"` deve encontrar `"Bolo fofinho de cenoura"`.
2. **Busca com stopwords:** `"bolo com cenoura"` deve encontrar `"Bolo de Cenoura"`.
3. **Busca por ingrediente:** `"chocolate"` encontra bolo com ingrediente `"Chocolate em pó"`.
4. **Busca por passo de preparo:** `"liquidificador"` encontra receita cujo modo de preparo diga `"Bata tudo no liquidificador"`.
5. **Busca por ID e texto misto:** `"1"` prioriza receita de ID 1; `"4"` acha ID 4 e receita `"Pizza 4 queijos"`.
6. **Busca sem acentuação:** `"macarrao"` encontra `"Macarrão ao alho e óleo"`.

---

## 🧪 Como Testar e Ter Certeza que Funciona
Execute:
```bash
./gradlew test --tests "ChefBot_Telegram.service.RecipeServiceSearchTest"
```

### Critério de Sucesso:
- Todos os testes de busca passam com `BUILD SUCCESSFUL`.
- Nenhuma busca válida do usuário retorna lista vazia indevidamente.
