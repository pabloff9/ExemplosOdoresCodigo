package br.com.exercicios.quatro;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Start {
	public static List<Contato> contatos = new ArrayList<Contato>();
	
	public static void main(String[] args) {
		Scanner scanner;
		String entrada = "";
		
		
		do {
			scanner = new Scanner(System.in);
			
			System.out.println("Escolha uma opção");
			System.out.println("1 - Inseri contatos");
			System.out.println("2 - Listar Contatos");
			System.out.println("3 - sair");
			
			entrada = scanner.next();
			
			switch (entrada) {
			case "1":
				criarContato(contatos, scanner);
				break;

			case "2":
				listarContatos(contatos);
				break;
				
			case "3":
				break;
				
			default:
				System.out.println("Entrada invalida");
				break;
			}
		} while (!entrada.equals("3"));
		
	}
	
	public static void criarContato(List<Contato> contatos, Scanner scanner){
		if (contatos.size() < 10) {
			System.out.println("Digite o Nome: ");
			String nome = scanner.next();
			System.out.println("Digite o numero: ");
			String numero = scanner.next();
			contatos.add(new Contato(nome, numero));
			System.out.println("Contato Adicionado.");
		}else{
			System.out.println("Tamanho maximo da lista alcançado");
		}
	}
	
	public static void listarContatos(List<Contato> contatos){
		if (contatos.isEmpty()) {
			System.out.println("Lista esta vazia");
		}else{
			contatos.stream().forEach(x -> System.out.println(x.getNome() + " : " + x.getTelefone()));
		}
	}
}
