package br.com.exercicios.tres;

public class Data {
	private String dia;
	private String mes;
	private String ano;
	
	public String getDia() {
		return dia;
	}
	public void setDia(String dia) {
		if(validarTamanhoPermitido(2, dia) && validarValorMaximo(31, dia)){
			this.dia = dia;
		}
	}
	
	public String getMes() {
		return mes;
	}
	
	public void setMes(String mes) {
		if(validarTamanhoPermitido(2, mes) && validarValorMaximo(12, mes)){
			this.mes = mes;
		}
	}
	public String getAno() {
		return ano;
	}
	
	public void setAno(String ano) {
		if(validarTamanhoPermitido(4, mes)){
			this.ano = ano;
		}
	}
	
	public boolean validarTamanhoPermitido(int tamanho, String valor){
		if (valor.length() > tamanho) {
			System.out.println("Tamanho Invalido");
			return false;
		}
			return true;
	}
	
	public boolean validarValorMaximo(int valorMaximo, String valor){
		if (Integer.valueOf(valor) > valorMaximo){
			System.out.println("valor invalido");
			return false;
		}
		return true;
	}
	
	public String printarData(){
		return (this.dia+"/"+this.mes+"/"+this.ano+"/");
	}
	
}
