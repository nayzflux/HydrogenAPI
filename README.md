# HydrogenAPI

Socle Java partagé pour les plugins Paper et Velocity du réseau. Le projet contient trois modules Gradle : `common`, `paper-api` et `velocity-api`. Les deux derniers produisent des JARs installables ; les plugins de jeu les utilisent comme dépendances de compilation et récupèrent leurs services au runtime.

## Versions et construction

- Java 25 et Gradle 9.6.1 via le wrapper inclus.
- Paper API `26.2.build.129-stable` et Velocity API `4.2.0`, dernières versions stables vérifiées le 26 septembre 2026. Paper 26.3 est encore en canal alpha à cette date.
- EnderCloud Paper `fr.nayz.endercloud:paper:0.1.0` doit être publié dans Maven local avant la compilation de `paper-api`. Le dépôt EnderCloud fournit une tâche `:paper:publishToMavenLocal` dans son projet `plugins`.

```powershell
.\gradlew.bat build
.\gradlew.bat publishToMavenLocal
```

Les JARs à déployer sont `paper-api/build/libs/HydrogenPaper-0.7.3.jar` et `velocity-api/build/libs/HydrogenVelocity-0.7.3.jar`. Installez le premier avec le plugin EnderCloud Paper sur chaque serveur Paper et le second sur chaque proxy Velocity. Les coordonnées Maven locales destinées aux plugins consommateurs sont `fr.nayz.hydrogen:paper-api:0.7.3` et `fr.nayz.hydrogen:velocity-api:0.7.3`, à utiliser en `compileOnly`.

Les signatures publiques de matchmaking utilisent `QueueJoinResult` et `MatchAssignment`. Le plugin consommateur ne compile plus contre EnderCloud ; EnderCloud Paper reste installé au runtime. `HydrogenPaperApi.transfers()` fournit les transferts individuels et groupés vers le hub. `scoreboards()` donne accès à `Board` et `ScoreboardService` ; `GUI` et `ItemBuilder` sont dans `paper-api`. `chat()` formate le chat selon le grade en mémoire et accepte une couleur de pseudo choisie par le jeu. Paper et Velocity restent des dépendances `compileOnly` dans les projets consommateurs.

## Stockage et démarrage

Hydrogen conserve comptes, derniers pseudos connus, amis, groupes, achats, possessions et tickets de matchmaking dans une base PostgreSQL dédiée. Redis conserve la présence et l'annuaire des joueurs connectés pendant 45 secondes et diffuse les changements de grade, les notifications sociales et les changements de file. Les tables Hydrogen sont distinctes de celles d’EnderCloud. Tous les serveurs et proxies doivent joindre les mêmes services PostgreSQL et Redis.

Variables requises sur Paper et Velocity :

| Variable | Valeur |
| --- | --- |
| `HYDROGEN_DB_HOST` | Hôte PostgreSQL ou PgBouncer, par exemple `postgres` |
| `HYDROGEN_DB_PORT` | Port PostgreSQL ou PgBouncer, par exemple `5432` |
| `HYDROGEN_DB_NAME` | Nom de la base, par exemple `mc` |
| `HYDROGEN_DB_USER` | Utilisateur PostgreSQL Hydrogen |
| `HYDROGEN_DB_PASSWORD` | Mot de passe PostgreSQL Hydrogen |
| `HYDROGEN_REDIS_HOST` | Hôte Redis, par exemple `redis` |
| `HYDROGEN_REDIS_PORT` | Port Redis, par exemple `6379` |
| `HYDROGEN_REDIS_DB` | Index Redis, facultatif, `0` par défaut |
| `HYDROGEN_REDIS_USER` | Utilisateur Redis, facultatif |
| `HYDROGEN_REDIS_PASSWORD` | Mot de passe Redis, facultatif |
| `HYDROGEN_PROXY_ID` | Identifiant unique du proxy Velocity uniquement |

Chaque processus limite son pool JDBC à deux connexions. À 167 instances Paper et plusieurs proxies, placez PgBouncer devant PostgreSQL pour borner les connexions physiques. Exécutez les migrations une fois avant de déployer les plugins, avec une connexion directe à PostgreSQL :

```powershell
.\gradlew.bat :common:migrateDatabase
```

La tâche lit les mêmes variables de stockage que les plugins. Aucune URL de connexion n'est à fournir. Elle n’exécute rien sur le fil principal Minecraft. Après migration, le compte PostgreSQL des plugins peut utiliser uniquement les droits de lecture et d’écriture sur les tables Hydrogen ; la tâche de migration garde les droits de création et de modification du schéma.

HydrogenAPI accepte le mode d'authentification configuré sur Velocity et Paper, en ligne ou hors ligne. Il utilise l'UUID fourni par chaque plateforme sans vérifier le mode ni calculer un autre UUID. Le proxy doit transmettre aux serveurs Paper le même UUID pour que le joueur retrouve le même compte, grade et état de file. Un changement de mode peut produire un UUID différent et donc un autre compte Hydrogen. Les grades sont définis dans l'enum `fr.nayz.hydrogen.api.account.Rank`, avec leurs inclusions, préfixes et permissions. L'enum `PermissionNode` associe chaque nœud à Paper ou Velocity. Une modification de ces enums nécessite de redéployer les deux plugins. Les permissions et grades des joueurs présents sont actualisés par notification Redis et par relecture groupée toutes les minutes sur chaque plateforme.

## Utilisation depuis un plugin

Sur Paper, déclarez `depend: [HydrogenAPI]` dans `plugin.yml` et récupérez le service au démarrage :

```java
import fr.nayz.hydrogen.paper.HydrogenPaperApi;
import org.bukkit.Bukkit;

HydrogenPaperApi api = Bukkit.getServicesManager().load(HydrogenPaperApi.class);
if (api == null) {
    throw new IllegalStateException("HydrogenAPI is unavailable");
}

api.accounts().getOrCreate(playerId).thenAccept(account -> {
    long coins = account.coins();
    // Programmer les appels à Bukkit sur le fil principal si nécessaire.
});
```

Sur Velocity, déclarez `@Dependency(id = "hydrogenapi")` sur votre plugin et récupérez l’instance de `hydrogenapi` via `ProxyServer.getPluginManager()`. Cette instance implémente `HydrogenVelocityApi`.

Les services et modèles communs sont rangés dans `fr.nayz.hydrogen.api.account`, `.friend`, `.party`, `.presence`, `.shop`, `.inventory` et `.matchmaking`. Le matchmaking Paper se trouve dans `fr.nayz.hydrogen.paper.matchmaking`.

## Grades, chat et permissions

`Account.rank()` renvoie désormais `Rank`, et `AccountService.setRank(playerId, Rank.VIP)` reçoit cette enum. Le champ PostgreSQL `rank_id` conserve son nom. Les anciens imports `RankCatalog` et les appels à `setRank` avec une chaîne doivent être remplacés. `STAFF` ne figure plus dans les grades ; réinitialisez les données de test qui portent cette valeur avant le déploiement. Aucune migration SQL n'est nécessaire.

| Grade | Inclut | Préfixe | Couleur du pseudo et du message |
| --- | --- | --- | --- |
| `PLAYER` | Aucun | Aucun | Gris |
| `VIP` | `PLAYER` | `[VIP]` jaune | Gris |
| `VIP_PLUS` | `VIP` | `[VIP+]` aqua | Gris |
| `MODERATOR` | `PLAYER` | `[Modérateur]` bleu | Or |
| `ADMIN` | `MODERATOR` | `[Admin]` rouge | Or |

Le chat Paper local utilise le format `préfixe pseudo » message`. Hydrogen installe son `ChatRenderer` à la priorité `LOWEST`, à partir du grade en mémoire ; un plugin de mini-jeu peut le remplacer dans un listener `AsyncChatEvent` de priorité ultérieure. Le chat ne lit pas la base et n'est pas relayé entre serveurs.

Chaque commande vérifie un nœud, indépendamment du nom du grade. `PLAYER` accorde `hydrogen.command.friend` et `hydrogen.command.party` sur Velocity ; les autres grades les héritent. `ADMIN` accorde les permissions de diagnostic décrites ci-dessous. Pour ajouter un droit Vanilla ou tiers, ajoutez son identifiant et sa plateforme dans `PermissionNode`, puis déclarez-le dans les permissions du grade voulu dans `Rank`. Les commandes Paper et tierces qui consultent `Player.hasPermission` reçoivent ces nœuds par `PermissionAttachment` ; les commandes visibles sont actualisées quand le grade change. Sur Velocity, Hydrogen ajoute ses nœuds au fournisseur de permissions déjà installé et laisse tous les autres nœuds à ce fournisseur. Un autre plugin peut toujours accorder des droits supplémentaires.

## Commandes de diagnostic admin

Sur Paper, `/matchmaking join <skywars-solo|skywars-duo> [solo|party]` lance une entrée dans la file depuis le serveur courant. Le mode par défaut est `solo` ; `party` envoie le groupe entier. `/matchmaking status` affiche l'état persistant et les tickets, `/matchmaking leave` tente la sortie et indique si EnderCloud la refuse, et `/matchmaking assignment` affiche l'affectation courante de ce serveur. La commande exige `hydrogen.admin.matchmaking`. Elle est réservée aux joueurs, car ses actions concernent leur propre ticket.

Sur Velocity, `/hydrogen account <pseudo|uuid>` consulte un compte sans en créer un, `/hydrogen queue <pseudo|uuid>` affiche son état de file et `/hydrogen rank <pseudo|uuid> <grade>` change son grade. Ces actions exigent respectivement `hydrogen.admin.account`, `hydrogen.admin.queue` et `hydrogen.admin.rank`. Un pseudo désigne le dernier pseudo enregistré ; si plusieurs comptes le portent, la commande donne leurs UUID. La consultation accepte aussi un joueur déconnecté. Les opérations de stockage restent asynchrones.

Seul `ADMIN` reçoit ces quatre permissions dans le catalogue initial. Elles restent des nœuds de permission : un autre fournisseur peut les accorder à un opérateur ou à un rôle distinct. Les commandes de diagnostic servent à tester les services avant la création des menus de jeu. Le changement de grade publie la notification Redis habituelle.

## Commandes sociales Velocity

Les deux commandes sont réservées aux joueurs. Les sous-commandes sont en anglais et les réponses en français.

| Commande | Sous-commandes |
| --- | --- |
| `/friend` | `add <pseudo>`, `requests [page]`, `accept <pseudo>`, `decline <pseudo>`, `remove <pseudo>`, `list [page]` |
| `/party` | `create`, `invite <pseudo>`, `invites [page]`, `accept <pseudo>`, `decline <pseudo>`, `list`, `leave`, `kick <pseudo>`, `leader <pseudo>`, `disband` |

Les nouvelles demandes et invitations ciblent les joueurs connectés sur le réseau, quel que soit leur proxy. Les listes affichent dix entrées par page et donnent l'UUID à utiliser si deux anciens pseudos se ressemblent. Une invitation de groupe crée un groupe si l'auteur n'en a pas. Tous les membres peuvent inviter par défaut ; le chef peut changer ce droit avec `PartyService.setMembersCanInvite`. Un joueur quitte son groupe lorsqu'il se déconnecte du proxy. Les invitations expirent après dix minutes, les groupes après 24 heures sans activité.

Le délai entre invitations répétées vers un même joueur progresse à 2, 5, 10 puis 30 secondes. Il revient au premier palier après dix minutes sans invitation. Chaque proxy accepte au plus dix tentatives par auteur en dix secondes. PostgreSQL valide le premier délai dans la transaction d'invitation ; une invitation refusée ne le fait pas progresser.

Exécutez les migrations V2 et V3 avant de démarrer les JARs `0.7.3`. V3 ajoute les tickets Hydrogen, leurs membres et les annonces de sélection uniques par joueur. La version `0.7.3` ne nécessite aucune nouvelle migration.

Les implémentations de `common` sont rangées par domaine dans `fr.nayz.hydrogen.core`, avec les accès SQL partagés dans `.database` et Redis dans `.redis`. Les handlers Paper et Velocity ont chacun un fichier et sont rangés par responsabilité sous `paper.event` et `velocity.event`. Le point d’entrée Velocity conserve seulement le handler d’initialisation nécessaire à l’enregistrement des autres listeners.

Les opérations de stockage et Redis renvoient des `CompletableFuture`. Ne faites pas `join()` ou `get()` dans un événement Paper. Les erreurs métier remontent comme `HydrogenException`, avec un `HydrogenErrorReason` stable. Un achat ou un changement de coins exige un `operationId` UUID stable pour chaque action utilisateur : réutilisez cet UUID lors d’un retry.

La boutique enregistre les offres du jeu au chargement du plugin. Chaque offre définit un prix en coins globaux et une possession abstraite attribuée dans la même transaction que le débit. Déployez la même définition d’offre sur toutes les instances d’un jeu. Les kits et cosmétiques restent des identifiants de possession ; le jeu construit ses `ItemStack` lui-même.

## Matchmaking persistant

`MatchmakingQueue` contient `SKYWARS_SOLO` et `SKYWARS_DUO`. Sur Paper, `enqueueSolo(queue, playerId)` et `enqueueParty(queue, playerId)` enregistrent d'abord un ticket Hydrogen avec ses membres figés, puis envoient une seule demande à EnderCloud. Tout membre du groupe peut lancer la file. `leaveQueue(playerId)` retire le ticket entier lorsqu'EnderCloud accepte la sortie ; `false` signifie que la sortie n'est plus possible à ce stade. Les erreurs EnderCloud sont propagées dans le `CompletableFuture`. Une indisponibilité garde le ticket en vérification ; un refus définitif 4xx retire l'intention.

`api.queueStates().get(playerId)` et `getMany(playerIds)` lisent l'état depuis PostgreSQL sur Paper comme sur Velocity. Les états sont `JOINING`, `QUEUED`, `VERIFYING`, `LEAVING` et `MATCH_FOUND`. Le temps d'attente commence à la première intention conservée ; il repart si EnderCloud renvoie un nouvel identifiant de ticket. Le Paper d'arrivée reprend la confirmation lorsque celle-ci est due, puis Hydrogen la renouvelle au plus une fois par ticket et par minute. EnderCloud reste responsable des files, affectations et transferts ; Hydrogen ne publie pas ses événements d'orchestration.

À l'arrivée du joueur, Paper lit l'état en arrière-plan. Il émet `QueueStateReadyEvent` sur son fil principal quand l'état initial est connu, puis `QueueStateChangedEvent` à chaque changement. **Le plugin de jeu ou du hub doit attendre `QueueStateReadyEvent` avant de construire son inventaire.** Tant que la file est active, Hydrogen efface l'inventaire et place une barrière dans la 3e case et un papier dans la 5e case de la barre rapide. Les objets sont marqués et ne peuvent être déplacés ou jetés. La barrière tente de quitter la file ; un clic droit sur le papier affiche la file et le temps d'attente. À la sortie ou à la sélection, Hydrogen retire ses objets avant `QueueStateChangedEvent`, puis le plugin consommateur peut reconstruire son inventaire. Aucun `ItemStack` de jeu n'est persisté.

Sur une instance de jeu qui possède déjà l'affectation EnderCloud du joueur, Hydrogen confirme `MATCH_FOUND` avant `QueueStateReadyEvent`. En cas d'échec de cette confirmation, il attend et réessaie sans afficher les objets de file. Velocity relit l'état trois secondes après un changement de serveur avant de signaler que la file continue.

Velocity lit ses joueurs par lot toutes les 15 secondes et leur rappelle la file et le temps écoulé en français. Il annonce la continuité de la file après un changement de serveur et annonce une partie trouvée une seule fois par ticket et par joueur, après confirmation de sélection par EnderCloud ou d'une affectation sur le Paper cible. Une déconnexion complète du proxy efface le ticket Hydrogen du groupe ; un changement de serveur Paper le conserve.

## Tests

Les tests d’intégration utilisent de vrais PostgreSQL et Redis, avec des données de test isolées. Sur une machine avec Docker :

```powershell
docker compose -f compose.test.yml up -d
$env:HYDROGEN_TEST_DB_HOST = 'localhost'
$env:HYDROGEN_TEST_DB_PORT = '55432'
$env:HYDROGEN_TEST_DB_NAME = 'hydrogen_test'
$env:HYDROGEN_TEST_DB_USER = 'hydrogen_test'
$env:HYDROGEN_TEST_DB_PASSWORD = 'hydrogen_test'
$env:HYDROGEN_TEST_REDIS_HOST = 'localhost'
$env:HYDROGEN_TEST_REDIS_PORT = '56379'
.\gradlew.bat test
```

Le test de charge synthétique est séparé des tests ordinaires :

```powershell
$env:HYDROGEN_LOAD_TEST = 'true'
.\gradlew.bat :common:test --tests 'fr.nayz.hydrogen.core.integration.HydrogenLoadTest'
```

Il crée et relit 2 000 comptes et présences sous 167 identifiants de serveur avec deux instances de l’API. Il vérifie l’absence de perte dans ce scénario ; il ne mesure pas le coût de 167 processus Paper réels. Un test optionnel redémarre le conteneur Redis et vérifie le retour de la présence et des notifications :

```powershell
$env:HYDROGEN_RECONNECT_TEST = 'true'
$env:HYDROGEN_REDIS_CONTAINER = 'hydrogen-api-test-redis'
.\gradlew.bat :common:test --tests 'fr.nayz.hydrogen.core.redis.RedisReconnectIntegrationTest'
```
