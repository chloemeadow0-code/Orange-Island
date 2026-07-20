# Fournisseurs API

橘子岛 se connecte directement aux fournisseurs d'IA — sans intermédiaire, sans abonnement, sans télémétrie. Vous apportez vos propres clés API et tout fonctionne depuis votre appareil.

## Fournisseurs Intégrés

| Fournisseur | URL de Base | Modèles | Notes |
|-------------|-------------|---------|-------|
| **Google** | `https://generativelanguage.googleapis.com/v1beta` | Série Gemini | Offre gratuite disponible via Google AI Studio |
| **OpenAI** | `https://api.openai.com/v1` | GPT-4, GPT-4o, série o | Modèles de raisonnement pris en charge |
| **Anthropic** | `https://api.anthropic.com/v1` | Série Claude | Réflexion étendue prise en charge |
| **DeepSeek** | `https://api.deepseek.com/v1` | DeepSeek-V3, DeepSeek-R1 | Modèles de raisonnement pris en charge |
| **Qwen** | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | Série Qwen | Via Alibaba DashScope |
| **Ollama** | `http://localhost:11434/v1` | Tout modèle téléchargé | Auto-hébergé, pas de clé API nécessaire |
| **OpenRouter** | `https://openrouter.ai/api/v1` | Multi-fournisseurs | Accédez à de nombreux modèles via une seule API |
| **Local** | N/D | Modèles GGUF | Sur l'appareil via llama.cpp, entièrement hors ligne |

## Changer de Fournisseur

Appuyez sur le sélecteur de fournisseur dans Paramètres pour basculer entre les fournisseurs. Chaque fournisseur conserve ses propres :

- Clés API
- URL de base (modifiable pour les proxys/auto-hébergés)
- Liste de modèles

---

## Clés API

### Plusieurs Clés par Fournisseur

Chaque fournisseur prend en charge plusieurs clés API nommées. Cela permet :

- **Rotation** — basculer entre des clés pour différents niveaux d'utilisation
- **Organisation** — séparer l'utilisation professionnelle et personnelle
- **Secours** — garder une clé de sauvegarde prête

### Gérer les Clés

1. Allez dans **Paramètres → Fournisseur**
2. Sélectionnez un fournisseur
3. Sous **Clés API**, appuyez sur **Ajouter une Nouvelle Clé**
4. Saisissez un **nom** (par ex., "Travail", "Personnel", "Équipe Partagée") et la **valeur de la clé**
5. Appuyez sur **Ajouter**

Appuyez sur le bouton radio pour définir la clé active. Appui long sur une clé pour **Modifier** ou **Supprimer**.

### Sécurité des Clés

!!! warning
    Les clés API sont stockées localement dans une base de données Room chiffrée. Elles ne sont jamais envoyées aux serveurs 橘子岛 (il n'y en a pas). Cependant, elles sont exportées en texte brut si vous les incluez dans un fichier d'export `.橘子岛`.

---

## Fournisseurs Personnalisés

Ajoutez n'importe quel point de terminaison compatible OpenAI :

1. Allez dans **Paramètres → Fournisseur**
2. Appuyez sur **+ Ajouter un Fournisseur Personnalisé** en bas de la liste des fournisseurs
3. Saisissez :
    - **Nom du Fournisseur** — n'importe quel nom d'affichage
    - **URL de Base** — le point de terminaison API
4. Appuyez sur **Ajouter**

橘子岛 récupère la liste des modèles depuis `{url_de_base}/v1/models`. Une fois ajoutés, les fournisseurs personnalisés fonctionnent exactement comme les fournisseurs intégrés : ajoutez des clés API, synchronisez les modèles et discutez.

### Cas d'Utilisation

- **Auto-hébergé** — connectez-vous à vLLM, LocalAI, text-generation-webui ou d'autres serveurs compatibles OpenAI
- **Proxys** — routez via un proxy d'entreprise ou une passerelle API
- **Points de terminaison alternatifs** — utilisez Azure OpenAI, Cloudflare AI Gateway ou d'autres services compatibles

### Renommer ou Supprimer

Appui long sur un fournisseur personnalisé pour **Renommer** ou **Supprimer**. La suppression retire le fournisseur et toutes ses clés.

!!! warning
    Les fournisseurs intégrés ne peuvent pas être renommés ou supprimés.

---

## Remplacement de l'URL de Base

Chaque fournisseur (y compris les intégrés) a une **URL de Base** modifiable. C'est utile pour :

- **Proxys** : Router via `https://mon-proxy.exemple.com/v1`
- **Auto-hébergé** : Pointer vers votre propre instance
- **Routage régional** : Utiliser des points de terminaison spécifiques à une région

---

## Synchronisation des Modèles

Après avoir ajouté des clés API, synchronisez la liste des modèles :

1. Allez dans **Paramètres → Modèles**
2. Appuyez sur **Synchroniser depuis Tous les Fournisseurs**
3. 橘子岛 récupère les modèles disponibles de chaque fournisseur configuré

Une barre de notification montre la progression et les résultats de la synchronisation. Vous pouvez ensuite activer/désactiver des modèles individuels et définir un modèle par défaut.

---

## Notes Spécifiques aux Fournisseurs

### Google Gemini

- Clés API depuis [Google AI Studio](https://aistudio.google.com/apikey)
- Offre gratuite disponible avec limites de débit
- Prend en charge l'exécution de code et le grounding de recherche (outils intégrés)

### OpenAI

- Clés API depuis [Platform](https://platform.openai.com/api-keys)
- Les modèles de raisonnement (o1, o3) nécessitent un accès API spécifique
- Streaming, outils et vision tous pris en charge

### Anthropic

- Clés API depuis [Console](https://console.anthropic.com/)
- Réflexion étendue avec budgets de tokens configurables
- Utilisation d'outils avec appels parallèles prise en charge

### Ollama

- Pas de clé API requise (réseau local)
- URL de base typiquement `http://<hôte>:11434/v1`
- Liste de modèles récupérée depuis l'API d'Ollama
- Voir [FAQ](faq.md) pour le dépannage spécifique à Ollama

### OpenRouter

- Une seule clé API pour plus de 200 modèles
- Tarification au token variable selon le modèle
- Idéal pour essayer différents modèles sans comptes fournisseurs individuels

### Local (llama.cpp)

- Aucun réseau requis
- Fichiers de modèle GGUF stockés sur l'appareil
- Voir [Modèles Locaux](local-model.md) pour la configuration
