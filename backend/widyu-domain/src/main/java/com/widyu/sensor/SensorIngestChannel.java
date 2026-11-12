package com.widyu.sensor;

/** 센서 묶음이 서버에 들어온 길(LLD-0128). 폰은 WebSocket을 먼저 쓰고 실패하면 REST로 다시 보낸다. */
public enum SensorIngestChannel {
    WS,
    REST
}
