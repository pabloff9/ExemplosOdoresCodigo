package br.com.exercicios.dois;

import java.util.Random;
import java.util.Scanner;

public class Start {
	public static void main(String[] args) {
		String valor = "";
		ContaCorrente conta = null;
		System.out.println("Escolha uma opção professor");
		
		do {
			
			System.out.println("1 - Criar uma conta");
			System.out.println("2 - Recuperar saldo");
			System.out.println("3 - Sacar");
			System.out.println("4 - Depositar");
			System.out.println("5 - Sair");
			
			Scanner scanner = new Scanner(System.in);
			valor = scanner.next();
			
			switch (valor) {
			case "1":
				conta = Start.criarConta(scanner);
				break;
			case "2":
				System.out.println(Start.recuperarSaldo(conta));
				break;
			case "3":
				Start.sacar(scanner, conta);
				break;
			case "4":
				Start.depositar(scanner, conta);
				break;
			case "5":
				System.out.println("Volte Sempre");
				break;
			default:
				System.out.println("opção inexistente");
				break;
			}
		} while (!valor.equals("5"));
	}
	
	private static boolean validarConta(ContaCorrente conta) {
		if (conta == null) {
			System.out.println("Crie uma conta primeiro");
			return false;
		}
		return true;
	}
	
	public static ContaCorrente criarConta(Scanner scanner){
		System.out.println("Digite o seu nome:");
		String nome = scanner.next();
		return new ContaCorrente(nome, new Random().nextInt(400));
	}
	
	public static double recuperarSaldo(ContaCorrente conta){
		if (validarConta(conta)) {
			return conta.getSaldo();
		}
		return 0;
	}
	
	public static void sacar(Scanner scanner, ContaCorrente conta){
		if (validarConta(conta)) {
			System.out.println("Digite um valor para saque");
			String entrada = scanner.next();
			try {
				Double valor = Double.valueOf(entrada);
				conta.sacar(valor);
			} catch (Exception e) {
				System.out.println("valor digitado nao e valido");
			}
		}
	}
	
	public static void depositar(Scanner scanner, ContaCorrente conta){
		if (validarConta(conta)) {
			System.out.println("Digite um valor para deposito");
			String entrada = scanner.next();
			try {
				Double valor = Double.valueOf(entrada);
				conta.depositar(valor);
			} catch (Exception e) {
				System.out.println("valor digitado nao e valido");
			}
		}
	}
}
