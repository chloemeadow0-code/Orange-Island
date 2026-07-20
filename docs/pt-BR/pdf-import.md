# Importação de PDF

O 橘子岛 pode extrair e enviar páginas selecionadas de arquivos PDF como imagens para modelos com capacidade de visão.

## Como Funciona

1. Anexe um arquivo PDF no chat
2. Um diálogo é aberto mostrando todas as páginas como miniaturas
3. Selecione quais páginas enviar
4. Confirme — o 橘子岛 extrai as páginas como imagens e as envia ao modelo

O modelo recebe as páginas como entrada de visão, permitindo que ele leia e analise o conteúdo do PDF.

---

## Seleção de Páginas

### Escolhendo Páginas

- Cada miniatura de página tem uma **caixa de seleção** no canto superior esquerdo
- Toque na caixa de seleção para ativar/desativar uma página
- Botões **Selecionar Todas** / **Desmarcar Todas** para operações em lote
- A barra superior mostra a contagem de selecionadas (ex.: "3 selecionadas")
- Máximo de **50 páginas** por PDF (arquivos maiores são truncados)

### Pré-visualização em Tela Cheia

Toque em qualquer miniatura para abrir o visualizador em tela cheia:

| Gesto | Ação |
|-------|------|
| Deslizar esquerda/direita | Navegar entre páginas |
| Tocar | Mostrar/ocultar os controles sobrepostos |
| Pinçar | Aumentar/diminuir zoom |
| Cápsula no canto inferior esquerdo | Alternar seleção da página |

A pré-visualização permite inspecionar páginas antes de decidir quais enviar.

---

## Enviando Páginas

Após selecionar as páginas, toque no botão de confirmação. O 橘子岛:

1. Renderiza cada página PDF selecionada como uma imagem de alta resolução
2. Anexa as imagens à sua mensagem
3. As envia ao modelo (requer um modelo com capacidade de visão)

---

## Limitações

- **Máximo de 50 páginas** por PDF — arquivos maiores são truncados
- **Apenas imagem**: O texto não é extraído; o modelo lê as páginas visualmente
- **Modelo de visão necessário**: O modelo de chat ativo deve suportar entradas de imagem
- **Tamanho do arquivo**: Embora não haja um limite rígido de tamanho de PDF, arquivos muito grandes podem ser lentos para renderizar

---

## Casos de Uso

- **Análise de documentos** — envie um contrato, relatório ou artigo para o modelo revisar
- **Pesquisa** — compartilhe páginas específicas de artigos acadêmicos
- **Tradução** — envie documentos em idiomas estrangeiros para tradução
- **Resumo** — obtenha resumos de documentos longos, página por página
