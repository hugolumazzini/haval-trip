package com.autolink.clusterservice;

import com.autolink.cluster.ClusterMsgData;
import com.autolink.clusterservice.IClusterCallback;

/**
 * O serviço do painel de instrumentos da central.
 *
 * A ordem dos métodos faz parte do contrato: o AIDL numera as transações pela
 * posição em que aparecem, e uma linha fora de lugar chamaria outra coisa do
 * lado de lá. Só o `registerCallback` nos interessa — é por ele que chega o
 * aviso de troca de página das bolas.
 */
interface IClusterService {
    void registerCallback(in IClusterCallback callback);
    void unregisterCallback(in IClusterCallback callback);
    void setMsg(int msgId, in ClusterMsgData data);
    void getMsg(int msgId);
    ClusterMsgData getMsgData(int msgId);
}
