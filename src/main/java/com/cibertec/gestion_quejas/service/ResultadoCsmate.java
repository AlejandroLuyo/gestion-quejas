package com.cibertec.gestion_quejas.service;

public class ResultadoCsmate {

    private final boolean puedeResolver;
    private final String respuesta;
    private final String motivoEscalamiento;

    public ResultadoCsmate(boolean puedeResolver, String respuesta, String motivoEscalamiento) {
        this.puedeResolver = puedeResolver;
        this.respuesta = respuesta;
        this.motivoEscalamiento = motivoEscalamiento;
    }

    public boolean isPuedeResolver() {
        return puedeResolver;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public String getMotivoEscalamiento() {
        return motivoEscalamiento;
    }
}