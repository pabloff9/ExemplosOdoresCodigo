package br.com.exercicios.dois;

public class ContaCorrente {
	
	private String nome;
	private int numero;
	private double saldo;
	
	public String getNome() {
		return nome;
	}
	
	public ContaCorrente(String nome, int numero) {
		setNome(nome);
		setNumero(numero);
		setSaldo(0);
	}
	
	public void sacar(double valor){
		if (valor > getSaldo()) {
			System.out.println("Valor de saque maior que valor da conta");
		}else{
			setSaldo(getSaldo() - valor);
		}
	}
	
	public void depositar(double valor){
		setSaldo(getSaldo() + valor);
	}

	public double getSaldo() {
		return saldo;
	}

	public void setSaldo(double saldo) {
		this.saldo = saldo;
	}
	
	public void setNome(String nome) {
		this.nome = nome;
	}
	
	public int getNumero() {
		return numero;
	}
	
	public void setNumero(int numero) {
		this.numero = numero;
	}
}
