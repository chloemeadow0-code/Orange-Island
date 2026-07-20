# Transcription d'Images

Permettez à un modèle de vision de décrire des images pour que les modèles texte seul puissent les comprendre.

## Ce Que Cela Fait

Lorsque vous envoyez une image à un modèle texte seul, 橘子岛 peut utiliser un modèle de vision séparé pour générer d'abord une description textuelle de l'image. Cette description est ensuite incluse dans le prompt envoyé à votre modèle principal.

Cela vous permet d'utiliser des images avec n'importe quel modèle, même ceux qui ne prennent pas nativement en charge la vision.

## Configuration

1. Allez dans **Paramètres → Transcription d'Images**
2. Choisissez un **Modèle de Transcription** — cela doit être un modèle compatible vision (par ex., GPT-4o, Gemini Flash, Qwen-VL)
3. Ajoutez des modèles aux **Modèles Activés** — ce sont les modèles texte seul qui recevront les descriptions d'images
4. Ajustez la **Taille de Lot** si vous envoyez beaucoup d'images à la fois (combien d'images décrire par appel API)

!!! tip "Modèles de Vision Locaux"
    Vous pouvez utiliser un modèle de vision local (avec mmproj) comme modèle de transcription. Cela garde le traitement d'images sur l'appareil.

## Comment Ça Marche

1. Vous joignez une image à votre message
2. 橘子岛 détecte que votre modèle actuel ne prend pas en charge la vision
3. L'image est d'abord envoyée au modèle de transcription
4. Le modèle de transcription génère une description textuelle
5. Cette description est ajoutée avant le texte de votre message
6. Le texte combiné est envoyé à votre modèle principal

---

## Taille de Lot

Contrôle combien d'images sont décrites par appel API au modèle de transcription.

- **1** — Décrire une image à la fois (plus d'appels API, plus précis)
- **5–10** — Décrire plusieurs images par appel (moins d'appels API, peut perdre des détails)

La valeur par défaut dépend de l'appareil. Des valeurs plus basses donnent de meilleurs résultats mais coûtent plus cher.

---

## Sélection de Modèle

### Modèle de Transcription

C'est le modèle de vision qui génère les descriptions d'images. Choisissez le modèle de vision le plus performant à votre disposition.

### Modèles Activés

Ce sont les modèles texte seul qui utiliseront la transcription d'images. Seuls les modèles de cette liste recevront des descriptions d'images transcrites. Les autres modèles recevront les images directement (s'ils les prennent en charge) ou pas du tout.
