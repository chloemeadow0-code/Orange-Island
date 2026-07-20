# Génération de Titres

Générez automatiquement des titres de conversation basés sur le premier échange.

## Ce Que Cela Fait

Lorsque vous démarrez une nouvelle conversation, 橘子岛 peut générer automatiquement un titre court et significatif basé sur votre premier message et la réponse du modèle. Cela remplace le titre générique "Nouveau Chat".

## Configuration

1. Allez dans **Paramètres → Génération de Titres**
2. Activez **Génération automatique des titres**
3. Choisissez éventuellement un **Modèle** pour la génération de titres (utilise le modèle de conversation actuel par défaut)

!!! tip "Choix du Modèle"
    La génération de titres utilise très peu de tokens. Vous pouvez utiliser un modèle économique et rapide (comme GPT-4o Mini ou un modèle local) sans affecter la qualité de votre conversation.

## Comment Ça Marche

1. Vous envoyez votre premier message dans une nouvelle conversation
2. Le modèle répond (comme d'habitude)
3. Une fois la réponse terminée, 橘子岛 envoie une petite requête séparée pour générer un titre
4. Le titre généré est enregistré et affiché dans la liste des conversations

La génération de titres ne s'exécute qu'une seule fois par conversation, lors du premier échange.

## Modèle de Génération de Titres

Vous pouvez utiliser un modèle différent spécifiquement pour la génération de titres :

- **Par Défaut** (aucune sélection) — Utilise le même modèle que la conversation
- **Modèle spécifique** — Utilise toujours ce modèle pour toutes les générations de titres, quel que soit le modèle utilisé pour la conversation

Utiliser un modèle rapide dédié pour les titres peut réduire la latence et le coût.
