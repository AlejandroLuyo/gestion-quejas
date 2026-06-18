package com.cibertec.gestion_quejas.service;

public class ResultadoTurno {

    public enum Estado { CONTINUAR, CERRAR_SATISFECHO, ESCALAR }

    private final Estado estado;
    private final String respuesta;
    private final String motivoEscalamiento;

    public ResultadoTurno(Estado estado, String respuesta, String motivoEscalamiento) {
        this.estado = estado;
        this.respuesta = respuesta;
        this.motivoEscalamiento = motivoEscalamiento;
    }

    public Estado getEstado() {
        return estado;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public String getMotivoEscalamiento() {
        return motivoEscalamiento;
    }
}