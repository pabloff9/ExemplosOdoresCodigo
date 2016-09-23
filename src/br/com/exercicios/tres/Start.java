package br.com.exercicios.tres;

import java.util.Scanner;

public class Start {
	public static void main(String[] args) {
		
		
		Data data = new Data();
		Scanner scanner = new Scanner(System.in);
		String entrada = "";
		
		do {
			System.out.println("Escolha uma opção");
			System.out.println("1 - Inserir/ Trocar dia");
			System.out.println("2 - Inserir/ Trocar mes");
			System.out.println("3 - Inserir/ Trocar ano");
			System.out.println("4 - Visualizar Data");
			System.out.println("5 - sair");
			
			entrada = scanner.next();
			
			switch (entrada) {
			case "1": 
				alterarDia(scanner, data);
				break;
				
			case "2": 
				alterarMes(scanner, data);
				break;
			
			case "3": 
				alterarAno(scanner, data);
				break;
				
			case "4": 
				mostrarData(data);
				break;
				
			case "5": 
				break;
				
			default:
				System.out.println("Valor invalido");
				break;
			}
		} while (!entrada.equals("5"));
	}
	
	public static boolean validarEntradaVazia(String entrada){
		return !entrada.isEmpty();
	}
	
	public static void alterarDia(Scanner scanner, Data data){
		System.out.println("Digite o novo dia");
		String dia = scanner.next();
		data.setDia(dia);
	}
	
	public static void alterarMes(Scanner scanner, Data data){
		System.out.println("Digite o novo mes");
		String mes = scanner.next();
		data.setMes(mes);
	}
	
	public static void alterarAno(Scanner scanner, Data data){
		System.out.println("Digite o novo ano");
		String ano = scanner.next();
		data.setAno(ano);
	}
	
	public static void mostrarData(Data data) {
		System.out.println(data.printarData());
	}
}
