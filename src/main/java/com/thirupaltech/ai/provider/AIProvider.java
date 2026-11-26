package com.thirupaltech.ai.provider;

import java.time.Duration;

public interface AIProvider {

    String name();

    Duration requestTimeout();

    String generate(String systemPrompt, String message);
}
