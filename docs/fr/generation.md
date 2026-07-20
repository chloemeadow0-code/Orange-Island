# Paramètres de Génération

Contrôlez comment les modèles génèrent des réponses — de la longueur du contexte aux paramètres de créativité.

## Fenêtre de Contexte

**Messages Max de Contexte** définit combien de messages récents sont envoyés au modèle comme contexte. Par défaut : **20**.

- **5–20** — Contexte plus court, réponses plus rapides, moins d'utilisation de tokens
- **20–50** — Contexte plus long pour des conversations complexes et multi-tours
- **50–100** — Contexte maximal pour des discussions très longues (peut atteindre les limites de tokens)

Cela s'applique à tous les modèles. La taille réelle de la fenêtre de contexte en tokens dépend de votre modèle et de la longueur des messages.

---

## Température

Contrôle l'aléatoire dans la sortie du modèle. Plage : **0,0 – 2,0**.

- **0,0 – 0,3** — Plus déterministe, cohérent, factuel
- **0,5 – 0,8** — Créativité équilibrée (par défaut recommandé)
- **1,0 – 2,0** — Plus aléatoire, créatif, imprévisible

Une température plus élevée signifie que le modèle est plus susceptible de choisir des mots moins probables. Une température plus basse produit des sorties plus ciblées et répétitives.

!!! tip "Quand Ajuster"
    - **Code / Faits** : Utilisez une température basse (0,0 – 0,3)
    - **Écriture Créative** : Utilisez une température élevée (0,8 – 1,2)
    - **Chat Général** : Utilisez une température moyenne (0,5 – 0,7)

---

## Top P (Échantillonnage par Noyau)

Contrôle la diversité de la sélection de tokens. Plage : **0,0 – 1,0**.

Le modèle ne considère que le plus petit ensemble de tokens dont la probabilité cumulée dépasse `top_p`.

- **0,1** — Très ciblé, seulement les tokens les plus probables
- **0,5** — Diversité modérée
- **0,9 – 1,0** — Pleine diversité (par défaut recommandé)

Généralement, vous ajustez *soit* la température *soit* top P — pas les deux.

---

## Tokens Max par Défaut

Définit une limite maximale de tokens pour les réponses du modèle. Lorsqu'elle est définie, le modèle ne générera pas plus de ce nombre de tokens dans une seule réponse. Lorsqu'elle **n'est pas définie** (par défaut), le maximum propre du modèle s'applique.

Préréglages disponibles :

```
256   512   1024   2048
4096  8192  16384  32768
```

!!! tip "Laissez Non Défini pour la Flexibilité"
    Pour la plupart des cas d'utilisation, laissez ce paramètre non défini. Définissez une limite uniquement lorsque vous avez besoin de longueurs de réponse cohérentes (par ex., résumés courts) ou pour plafonner les coûts.

---

## Pénalité de Fréquence

Réduit la tendance du modèle à répéter les mêmes mots. Plage : **-2,0 – 2,0**.

- **Valeurs positives** (0,1 – 1,0) — Décourager la répétition
- **Zéro** (0,0) — Aucune pénalité (par défaut)
- **Valeurs négatives** (-1,0 – -0,1) — Encourager la répétition

---

## Pénalité de Présence

Encourage le modèle à parler de nouveaux sujets. Plage : **-2,0 – 2,0**.

- **Valeurs positives** (0,1 – 1,0) — Encourager la diversité des sujets
- **Zéro** (0,0) — Aucune pénalité (par défaut)
- **Valeurs négatives** — Rester sur le sujet actuel

---

## Réflexion / Raisonnement

Active le raisonnement en chaîne de pensée pour les modèles pris en charge (par ex., DeepSeek R1, Qwen3, Claude).

Lorsqu'il est activé, le modèle génère un raisonnement interne avant de produire la réponse finale. Cela améliore la précision pour les tâches complexes mais prend plus de temps et utilise plus de tokens.

### Niveau de Réflexion

- **Bas** — Raisonnement minimal, plus rapide
- **Moyen** — Équilibré (par défaut)
- **Élevé** — Raisonnement maximal pour les problèmes complexes

!!! warning "Tous les Modèles ne Prennent pas en Charge la Réflexion"
    Le mode réflexion nécessite un modèle qui prend en charge les tokens de raisonnement. Si votre modèle ne le prend pas en charge, ce paramètre n'a aucun effet.

---

## Visualiser le Défilement du Contexte

Lorsqu'elle est activée, 橘子岛 indique visuellement quels messages sont inclus dans la fenêtre de contexte actuelle par rapport à ceux qui ont été retirés (exclus en raison de la limite de la fenêtre de contexte). Cela vous aide à comprendre :

- Quelle partie de votre conversation le modèle peut "voir"
- Quand les messages plus anciens sortent du contexte
- Si vous devez augmenter la fenêtre de contexte

La visualisation apparaît comme un marqueur subtil dans la vue de conversation.

---

## Comment Fonctionnent les Paramètres

Tous les paramètres de génération sont **nullable** — lorsqu'ils ne sont pas explicitement définis, ils ne sont pas envoyés au modèle, et le modèle utilise ses propres valeurs par défaut. Chaque paramètre a une option de réinitialisation pour remettre la valeur à "non défini".

---

## Remplacements par Conversation

Vous pouvez remplacer les paramètres de génération pour des conversations individuelles en utilisant la boîte de dialogue **Paramètres Avancés** dans l'écran de chat (appui long sur le bouton d'envoi ou utilisez le menu ⋮).
