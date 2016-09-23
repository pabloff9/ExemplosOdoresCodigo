package br.com.exercicios.um;

public class Contador {

	private int contadorEventos;

	public void zeraContador(){
		setContadorEventos(0);
	}
	
	public void incrementarContadorEmUmValor(){
		setContadorEventos(getContadorEventos() + 1);
	}

	public int getContadorEventos() {
		return contadorEventos;
	}

	public void setContadorEventos(int contadorEventos) {
		this.contadorEventos = contadorEventos;
	}
}
