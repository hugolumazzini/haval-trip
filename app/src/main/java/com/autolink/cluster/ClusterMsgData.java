package com.autolink.cluster;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * O pacotinho de dados que o serviço do painel manda junto com cada aviso.
 *
 * <p>Isto não é uma classe nossa por escolha: é o formato que o serviço
 * {@code com.autolink.clusterservice} da própria central grava no Parcel. Para
 * ler o aviso de troca de página temos de desempacotar exatamente na mesma
 * ordem em que ele empacota — um inteiro de bandeiras, e depois, só se a
 * bandeira estiver ligada, o valor. Ler fora de ordem não daria um valor
 * errado: daria lixo, porque o Parcel é uma fita de bytes sem nomes.
 *
 * <p>Está em Java, e não em Kotlin, porque o código que o AIDL gera é Java e
 * espera achar esta classe com este nome e neste pacote.
 */
public class ClusterMsgData implements Parcelable {

    /** Bandeira: o pacote traz um inteiro. */
    private static final int TEM_INTEIRO = 1;

    /** Bandeira: o pacote traz uma lista de inteiros. */
    private static final int TEM_LISTA = 2;

    private int bandeiras = 0;
    private int inteiro = 0;
    private int[] lista = new int[0];

    public int getIntValue() {
        return inteiro;
    }

    public int[] getIntArrayValue() {
        return lista;
    }

    public void setIntValue(int valor) {
        bandeiras |= TEM_INTEIRO;
        inteiro = valor;
    }

    public void setIntArrayValue(int[] valores) {
        bandeiras |= TEM_LISTA;
        lista = valores;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(bandeiras);
        if ((bandeiras & TEM_INTEIRO) != 0) {
            parcel.writeInt(inteiro);
        }
        if ((bandeiras & TEM_LISTA) != 0) {
            parcel.writeInt(lista.length);
            parcel.writeIntArray(lista);
        }
    }

    /** O AIDL chama isto quando o parâmetro é de mão dupla. */
    public void readFromParcel(Parcel parcel) {
        bandeiras = parcel.readInt();
        if ((bandeiras & TEM_INTEIRO) != 0) {
            inteiro = parcel.readInt();
        }
        if ((bandeiras & TEM_LISTA) != 0) {
            lista = new int[parcel.readInt()];
            parcel.readIntArray(lista);
        }
    }

    public static final Creator<ClusterMsgData> CREATOR = new Creator<ClusterMsgData>() {
        @Override
        public ClusterMsgData createFromParcel(Parcel parcel) {
            ClusterMsgData dados = new ClusterMsgData();
            dados.readFromParcel(parcel);
            return dados;
        }

        @Override
        public ClusterMsgData[] newArray(int tamanho) {
            return new ClusterMsgData[tamanho];
        }
    };

    @Override
    public String toString() {
        return "ClusterMsgData{" + inteiro + "}";
    }
}
