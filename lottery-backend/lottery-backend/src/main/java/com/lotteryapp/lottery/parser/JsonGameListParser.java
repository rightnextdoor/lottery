package com.lotteryapp.lottery.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedGameList;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class JsonGameListParser implements GameListParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public SourceType supportedSourceType() {
        return SourceType.JSON;
    }

    @Override
    public boolean supports(String parserKey) {
        if (parserKey == null) return true;
        return parserKey.trim().toUpperCase(Locale.ROOT).contains("GAME_LIST");
    }

    @Override
    public IngestedGameList parse(byte[] bytes, String stateCode) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Game list JSON is empty");
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(bytes);
        } catch (Exception e) {
            throw new BadRequestException("Failed to parse game list JSON");
        }

        JsonNode array = findFirstArray(root);
        if (array == null || !array.isArray()) {
            throw new BadRequestException("Game list JSON does not contain an array");
        }

        List<IngestedGameList.GameInfo> items = new ArrayList<>();
        for (JsonNode n : array) {
            if (!n.isObject()) continue;

            String gameKey = text(n, "gameKey", "key", "id", "gameId", "externalId");
            String displayName = text(n, "displayName", "name", "title");

            if (displayName == null || displayName.isBlank()) continue;
            if (gameKey == null || gameKey.isBlank()) gameKey = displayName;

            items.add(IngestedGameList.GameInfo.builder()
                    .gameKey(gameKey)
                    .displayName(displayName)
                    .build());
        }

        if (items.isEmpty()) {
            throw new BadRequestException("Parsed game list JSON but no games found");
        }

        return IngestedGameList.builder()
                .stateCode(stateCode)
                .games(items)
                .build();
    }

    private static JsonNode findFirstArray(JsonNode root) {
        if (root == null) return null;
        if (root.isArray()) return root;

        if (root.isObject()) {
            for (String k : List.of("data", "results", "items", "games")) {
                JsonNode n = root.get(k);
                if (n != null && n.isArray()) return n;
            }

            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getValue() != null && e.getValue().isArray()) return e.getValue();
            }
        }
        return null;
    }

    private static String text(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull()) {
                String s = v.asText(null);
                if (s != null && !s.isBlank()) return s;
            }
        }
        return null;
    }
}
