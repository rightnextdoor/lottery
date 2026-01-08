package com.lotteryapp.lottery.ingestion.source;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PowerballSourceClient {

    private final DrawSourceClient drawSourceClient;

    public PowerballSourceClient(DrawSourceClient drawSourceClient) {
        this.drawSourceClient = drawSourceClient;
    }

    public DrawSourceClient.FetchedContent fetch(String url) {
        return drawSourceClient.fetch(url, Map.of());
    }
}
