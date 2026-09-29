package fr.nayz.hydrogen.paper;

import fr.nayz.hydrogen.api.HydrogenApi;
import fr.nayz.hydrogen.paper.matchmaking.MatchmakingService;
import fr.nayz.hydrogen.paper.transfer.HubTransferService;
import fr.nayz.hydrogen.paper.scoreboards.ScoreboardService;
import fr.nayz.hydrogen.paper.rank.PaperChatRenderer;

public interface HydrogenPaperApi extends HydrogenApi {
    MatchmakingService matchmaking();

    HubTransferService transfers();

    ScoreboardService scoreboards();

    PaperChatRenderer chat();
}
