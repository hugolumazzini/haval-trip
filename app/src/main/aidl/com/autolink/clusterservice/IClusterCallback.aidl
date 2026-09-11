package com.autolink.clusterservice;

import com.autolink.cluster.ClusterMsgData;

/**
 * Avisos que o serviço do painel manda para quem se registrou.
 *
 * A forma é ditada pelo serviço do carro (`com.autolink.clusterservice`), não
 * por nós: só assim o descritor do binder bate e a chamada é aceita.
 */
interface IClusterCallback {
    void callbackMsg(int msgId, in ClusterMsgData data);
}
