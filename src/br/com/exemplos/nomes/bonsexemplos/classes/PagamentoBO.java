package br.com.exemplos.nomes.bonsexemplos.classes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;

import br.com.hojeti.dao.FeriadoDao;
import br.com.hojeti.dao.ParametroDao;
import br.com.hojeti.enumerator.CPROrdemSituacaoEnum;
import br.com.hojeti.enumerator.LancamentoOperacaoEnum;
import br.com.hojeti.exception.BOException;
import br.com.hojeti.helper.AtualizacaoMonetariaHelper;
import br.com.hojeti.model.CPR;
import br.com.hojeti.model.CPRCredito;
import br.com.hojeti.model.CPRDebito;
import br.com.hojeti.model.CPROperacao;
import br.com.hojeti.model.CPROrdem;
import br.com.hojeti.model.CPRTitulo;
import br.com.hojeti.model.CPRTituloProcessado;
import br.com.hojeti.model.Carteira;
import br.com.hojeti.model.Feriado;
import br.com.hojeti.model.LancamentoTipo;
import br.com.hojeti.model.Parametro;
import br.com.hojeti.model.Parceiro;
import br.com.hojeti.model.Referencia;
import br.com.hojeti.model.ReferenciaOperacao;
import br.com.hojeti.model.ReferenciaOperacaoOcorrencia;
import br.com.hojeti.recebimento.CreditoHelper;
import br.com.hojeti.utils.CPRUtils;
import br.com.hojeti.utils.DateUtils;
import br.com.hojeti.utils.NumberUtils;
import br.com.hojeti.utils.Utils;

/**
 * @author Hoje Tecnologia
 * @version 1.2.2
 */
public class PagamentoBO {

	/**
	 * Campo adicionado a operação de abatimento gerada automaticamente,
	 * conforme parametros do sistema.
	 */
	private static final String OBSERVACAO_ABATIMENTO_AUTOMATICO = "Abatimento concedido automaticamente, conforme parametro de valor de tolerância.";

	private Parametro parametro;

	private ParametroDao parametroDao = new ParametroDao();

	private ParceiroBO parceiroBO = new ParceiroBO();
	private CalculoMonetarioBO calculoMonetarioBO = new CalculoMonetarioBO();
	private List<Feriado> feriados;

	private CPRBO cprBO = new CPRBO();

	public PagamentoBO() {
		try {

			this.parametro = parametroDao.carregarParametro();
			this.feriados = new FeriadoDao().select();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public PagamentoBO(Parametro parametro) {
		try {
			if (parametro != null) {
				this.parametro = parametro;
			} else {
				this.parametro = parametroDao.carregarParametro();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Método para realizar o crédito de ordem de pagamento. O título processado
	 * deve ter o motivo do rejeito diferente de <b>NÂO ENCONTRADO</b>. Caso o
	 * título possua este motivo de rejeito o método retornará nulo.
	 * <ul>
	 * <li>O cálculo de multa e juros é efetuado em caso de títulos
	 * vencidos</li>
	 * <li>Em casos de pagamento a maior, o valor de crédito é adicionado ao
	 * imóveis ou conjunto de imóveis que compõem aquele título</li>
	 * <li>Os títulos cancelados são compensados e o saldo creditado serve como
	 * abatimento do débito ou em casos onde o débito está abatido, o mesmo se
	 * tornará crédito para a <i>referência</i></li>
	 * </ul>
	 * 
	 * @param tituloProcessado
	 *            Título processado pelo arquivo de retorno
	 * @return
	 * @throws BOException
	 * @version 1.2
	 */
	public CPRCredito creditarOrdemPagamento(CPRTituloProcessado tituloProcessado) throws BOException {

		if (Utils.getId(tituloProcessado.getCprTitulo()) > 0) {

			Carteira carteira = tituloProcessado.getCprTitulo().getCarteiraBancaria();

			BigDecimal valorPago = carteira.isCobrancaCredito() ? tituloProcessado.getValorRecebido()
					: tituloProcessado.getValorPago();

			CPRCredito credito = creditarOrdemPagamento(tituloProcessado.getCprTitulo(), valorPago,
					tituloProcessado.getTaxaAdicional(), tituloProcessado.getDtPagamento(),
					tituloProcessado.getDtCompensacao());

			tituloProcessado.setAprovado(true);

			return credito;
		}

		return null;
	}

	/**
	 * Método para realizar o crédito de ordem de pagamento.
	 * <ul>
	 * <li>O cálculo de multa e juros é efetuado em caso de títulos
	 * vencidos</li>
	 * <li>Em casos de pagamento a maior, o valor de crédito é adicionado ao
	 * imóveis ou conjunto de imóveis que compõem aquele título</li>
	 * <li>Os títulos cancelados são compensados e o saldo creditado serve como
	 * abatimento do débito ou em casos onde o débito está abatido, o mesmo se
	 * tornará crédito para a <i>referência</i></li>
	 * 
	 * </ul>
	 * 
	 * @param ordemPagamento
	 *            Ordem de pagamento que receberá o crédito
	 * @param valorPago
	 *            Valor pago
	 * @param valorTaxaAdicional
	 *            Taxa cobrada pelo banco (Apenas para títulos)
	 * @param dtPagamento
	 *            Data de pagamento da ordem
	 * @param dtCompensacao
	 *            Data que o pagamento foi compensado
	 * @return Entidade de crédito referente ao pagamento
	 * @throws BOException
	 * @version 1.2.1
	 */
	public CPRCredito creditarOrdemPagamento(CPROrdem ordemPagamento, BigDecimal valorPago,
			BigDecimal valorTaxaAdicional, Date dtPagamento, Date dtCompensacao) throws BOException {

		try {

			CPRCredito credito = creditarOrdem(ordemPagamento, valorPago, valorTaxaAdicional, dtPagamento,
					dtCompensacao);

			Date dtVencimentoUtil = getProximaDataUtil(ordemPagamento.getDtAgendamento());

			valorPago = valorPago.setScale(8, BigDecimal.ROUND_HALF_EVEN);

			validarDadosRecebimento(dtPagamento, dtCompensacao);

			if (CPROrdemSituacaoEnum.COMPENSADO.equals(ordemPagamento.getOrdemSituacao())) {

				// Título compensado
				creditarOrdemCompensada(ordemPagamento, valorPago, dtPagamento, dtCompensacao, credito);

			} else {

				// Pagamento convencional
				if (CollectionUtils.isNotEmpty(ordemPagamento.getCprOperacoes())) {

					for (CPROperacao operacao : ordemPagamento.getCprOperacoes()) {

						if (!Arrays
								.asList(LancamentoOperacaoEnum.CANCELADO, LancamentoOperacaoEnum.ABATIMENTO,
										LancamentoOperacaoEnum.CREDITO_RESGATE)
								.contains(operacao.getLancamentoOperacao())) {
							cancelarOperacao(operacao);
						}

					}
				}

				BigDecimal valorAPagar = recalcularMJC(ordemPagamento, dtPagamento, dtVencimentoUtil);

				creditarOrdemAberta(ordemPagamento, dtPagamento, dtCompensacao, valorPago, credito);

				calculaDesvioPagamentoMenor(ordemPagamento, valorPago, valorAPagar);

			}

			CreditoHelper.validarValorPago(ordemPagamento);

			return credito;

		} catch (Exception e) {
			e.printStackTrace();
			throw new BOException(e.getMessage(), e);
		}

	}

	/**
	 * Cancela os pagamentos dos títulos cancelados e recalculam seus valores.
	 * Caso existam abatimentos os mesmo são ignorados. Em caso de títulos
	 * parcelados, o valor referente a parcela que será recalculado.
	 * 
	 * @param ordemPagamento
	 * @param dtPagamento
	 * @param dtVencimentoUtil
	 * @param valorAPagar
	 * @return
	 * @throws BOException
	 * @version 1.0.1
	 */
	private BigDecimal recalcularMJC(CPROrdem ordemPagamento, Date dtPagamento, Date dtVencimentoUtil)
			throws BOException {
		BigDecimal valorAPagar = ordemPagamento.getValor();

		if (dtPagamento.after(dtVencimentoUtil)) {
			// Pagamento em atraso
			List<CPRDebito> debitos = CPRUtils.getDebitosOrdem(ordemPagamento);

			for (CPRDebito debito : debitos) {
				if (BooleanUtils.isTrue(debito.isAcrescimo())) {

					List<CPROperacao> operacoes = ordemPagamento.getCprOperacoes().stream()
							.filter(o -> debito.equals(o.getCprDebito())).collect(Collectors.toList());

					BigDecimal valorOperacoes = operacoes.stream().map(CPROperacao::getValor).reduce(BigDecimal.ZERO,
							BigDecimal::add);

					debito.getCprOperacoes().removeAll(operacoes);
					debito.setValor(debito.getValor().subtract(valorOperacoes));
					debito.setSaldoReceber(BigDecimal.ZERO);

					valorAPagar = valorAPagar.subtract(valorOperacoes);

					if (NumberUtils.isNotGreaterThenZero(debito.getValor())) {
						debito.getCpr().getCprDebitos().remove(debito);
					}

					ordemPagamento.getCprOperacoes().removeAll(operacoes);

					for (CPROperacao operacao : operacoes) {
						operacao.setCprOrdem(null);
					}
				}
			}

			List<CPRDebito> mjc = adicionarMJC(CPRUtils.getDebitosOrdem(ordemPagamento), dtPagamento);

			for (CPRDebito acrescimo : mjc) {
				valorAPagar = valorAPagar.add(acrescimo.getValor());
				CPROperacao provisionamento = provisionarDebito(acrescimo, acrescimo.getSaldoReceber(), dtPagamento,
						false, "");

				provisionamento.setCprOrdem(ordemPagamento);
				ordemPagamento.getCprOperacoes().add(provisionamento);

			}
		}

		return valorAPagar;
	}

	/**
	 * @param dtPagamento
	 * @param dtCompensacao
	 * @throws BOException
	 */
	private void validarDadosRecebimento(Date dtPagamento, Date dtCompensacao) throws BOException {
		if (dtPagamento == null || dtCompensacao == null) {
			throw new BOException("A data de pagamento ou compensação não podem ser nulas.",
					new NullPointerException());
		}
	}

	/**
	 * @param ordemPagamento
	 * @param valorPago
	 * @param valorTaxaAdicional
	 * @param dtPagamento
	 * @param dtCompensacao
	 * @return
	 * @throws BOException
	 */
	private CPRCredito creditarOrdem(CPROrdem ordemPagamento, BigDecimal valorPago, BigDecimal valorTaxaAdicional,
			Date dtPagamento, Date dtCompensacao) throws BOException {
		CPRCredito credito = cprBO.criarCredito(ordemPagamento, valorPago, dtPagamento, dtCompensacao);

		ordemPagamento.setDtCompensacao(dtCompensacao);

		if (ordemPagamento instanceof CPRTitulo) {
			((CPRTitulo) ordemPagamento).setValorTaxaAdicional(valorTaxaAdicional);
		}
		
		return credito;
	}

	/**
	 * Se o valor de tolerancia da aplicação, for igual ou menor que a a
	 * subtração do valor pago sobre a Ordem o sistema irá considerar como
	 * abatimento o valor restante.
	 * 
	 * @param ordemPagamento
	 * @param valorPago
	 * @param valorAPagar
	 * @throws Exception
	 * @version 1.0
	 */
	private CPROrdem calculaDesvioPagamentoMenor(CPROrdem ordemPagamento, BigDecimal valorPago, BigDecimal valorAPagar)
			throws Exception {

		BigDecimal tolerancia = parametro.getReceita().getValorTolerancia();

		valorPago = valorPago == null ? BigDecimal.ZERO : valorPago;

		valorAPagar = valorAPagar == null ? BigDecimal.ZERO : valorAPagar;

		if (NumberUtils.isGreaterThenZero(tolerancia) && NumberUtils.isGreaterThen(valorAPagar, valorPago)) {
			if (tolerancia.compareTo(valorAPagar.subtract(valorPago).abs()) >= 0) {

				Map<Object, BigDecimal> debitos = new HashMap<Object, BigDecimal>();
				for (CPRDebito debito : CPRUtils.getDebitosOrdem(ordemPagamento)) {
					debitos.put(debito, debito.getSaldoReceber());
				}

				Map<Object, BigDecimal> operacoesComAbatimento = NumberUtils.ratingValues(debitos,
						(valorAPagar.subtract(valorPago)));

				for (Entry<Object, BigDecimal> rating : operacoesComAbatimento.entrySet()) {

					if (rating.getValue().signum() != 0) {
						CPRDebito debito = (CPRDebito) rating.getKey();
						CPROperacao abatimento = abaterDebito(debito, ordemPagamento.getDtAgendamento(), false,
								rating.getValue(), OBSERVACAO_ABATIMENTO_AUTOMATICO);
						abatimento.setCprOrdem(ordemPagamento);
						ordemPagamento.getCprOperacoes().add(abatimento);
					}

				}
			}
		}

		return ordemPagamento;
	}

	/**
	 * Método privado para receber ordens de pagamento
	 * <ul>
	 * <li>Recebe títulos em aberto e cancelados.</li>
	 * <li>Para pagamento a maior, o valor do crédito é adicionado a(s)
	 * referência(s) que compõem aquela ordem.</li>
	 * <li>Para recebimentos sem saldo a receber. o valor que ultrapassar, será
	 * adicionado como crédito a(s) referência(s) da ordem</li> </ul
	 * 
	 * @param ordemPagamento
	 *            Ordem de pagamento
	 * @param dtPagamento
	 *            Data de pagamento da ordem
	 * @param dtCompensacao
	 *            Data de compensação do valor pago
	 * @param razaoPagamento
	 *            Valor entre 0 e 1 que implica na razão de pagamento do título
	 * @param recebimentoMaior
	 *            Boleana expressando se houve um pagamento a maior
	 * @param valorCredito
	 *            Valor de crédito a ser adicionado a(s) referência(s) da ordem
	 * @throws BOException
	 * @version 1.1
	 */
	private void creditarOrdemAberta(CPROrdem ordemPagamento, Date dtPagamento, Date dtCompensacao,
			BigDecimal valorPago, CPRCredito cprCredito) throws BOException {

		try {

			Map<Object, BigDecimal> operacoesValores = new HashMap<Object, BigDecimal>();
			BigDecimal valorCredito = BigDecimal.ZERO;

			ordemPagamento.setOrdemSituacao(CPROrdemSituacaoEnum.COMPENSADO);

			List<CPROperacao> operacoes = ordemPagamento.getCprOperacoes().stream()
					.filter(o -> !Arrays
							.asList(LancamentoOperacaoEnum.ABATIMENTO, LancamentoOperacaoEnum.CREDITO_RESGATE)
							.contains(o.getLancamentoOperacao()))
					.collect(Collectors.toList());

			for (CPROperacao operacao : operacoes) {

				operacao.setDtPagamento(dtPagamento);
				operacao.setDtCompensacao(dtCompensacao);
				operacao.setLancamentoOperacao(LancamentoOperacaoEnum.CREDITO);

				operacoesValores.put(operacao, operacao.getValor());

			}

			Map<Object, BigDecimal> valoresDistribuidos = NumberUtils.ratingValues(operacoesValores, valorPago, true);

			for (CPROperacao operacao : operacoes) {

				BigDecimal valorOperacao = valoresDistribuidos.get(operacao);

				CPRDebito debito = operacao.getCprDebito();

				if (NumberUtils.isGreaterThen(valorOperacao, operacao.getValor())) {
					valorCredito = valorCredito.add(valorOperacao.subtract(operacao.getValor()));
					valorOperacao = operacao.getValor();
				}

				operacao.setValor(valorOperacao);

				if (BooleanUtils.isTrue(debito.isAcrescimo())) {
					if (CollectionUtils.isNotEmpty(debito.getCprOperacoes())) {
						BigDecimal valor = debito.getCprOperacoes().stream()
								.filter(o -> !LancamentoOperacaoEnum.CANCELADO.equals(o.getLancamentoOperacao()))
								.map(CPROperacao::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
						debito.setValor(valor);
					} else {
						debito.setValor(valorOperacao);
					}

					debito.setSaldoReceber(BigDecimal.ZERO);
				} else {
					debito.setSaldoReceber(debito.getSaldoReceber().subtract(valorOperacao));
				}

			}

			if (NumberUtils.isGreaterThenZero(valorCredito)) {
				creditarOrdemCompensada(ordemPagamento, valorCredito, dtPagamento, dtCompensacao, cprCredito);
			}

			CPRUtils.getDebitosOrdem(ordemPagamento);

		} catch (Exception e) {
			e.printStackTrace();
			throw new BOException(e.getMessage(), e);
		}

	}

	// FIXME implementar método único para recebimento de ordens
	private void creditarOrdemCompensada(CPROrdem ordemPagamento, BigDecimal valorCredito, Date dtPagamento,
			Date dtCompensacao, CPRCredito cprCredito) throws BOException {

		Map<Object, BigDecimal> valoresCreditos = new HashMap<Object, BigDecimal>();

		try {

			for (CPROperacao operacao : ordemPagamento.getCprOperacoes()) {
				CPRDebito debito = operacao.getCprDebito();
				if (BooleanUtils.isFalse(debito.isAcrescimo())) {

					if (!valoresCreditos.containsKey(debito)) {
						valoresCreditos.put(debito, BigDecimal.ZERO);
					}

					valoresCreditos.put(debito, valoresCreditos.get(debito).add(operacao.getValor()));
				}
			}

			Map<Object, BigDecimal> creditosDistribuidos = NumberUtils.ratingValues(valoresCreditos, valorCredito,
					true);

			for (Object key : creditosDistribuidos.keySet()) {
				CPROperacao credito = new CPROperacao();
				CPRDebito debito = (CPRDebito) key;

				credito.setDtOperacao(DateUtils.now());
				credito.setDtPagamento(dtPagamento);
				credito.setDtCompensacao(dtCompensacao);
				credito.setDtBase(ordemPagamento.getDtAgendamento());
				credito.setLancamentoOperacao(LancamentoOperacaoEnum.CREDITO_REFERENCIA);
				credito.setValor(creditosDistribuidos.get(key));
				credito.setCprDebito(debito);
				credito.setCpr(debito.getCpr());
				credito.setCprOrdem(ordemPagamento);
				credito.setAutomatico(false);

				ordemPagamento.getCprOperacoes().add(credito);

			}

			adicionarCreditoParceiro(ordemPagamento, valorCredito, cprCredito);

		} catch (Exception e) {
			throw new BOException(e.getMessage(), e);
		}

	}

	/**
	 * Adicionar crédito as referências que compõem (método interno)
	 * 
	 * @param ordemPagamento
	 *            ordem de pagamento
	 * @param valorCredito
	 *            Valor do crédito a ser distribuído
	 * @throws BOException
	 */
	private void adicionarCreditoParceiro(CPROrdem ordemPagamento, BigDecimal valorCredito, CPRCredito credito)
			throws BOException {
		try {

			List<Referencia> referencias = CPRUtils.getReferenciasOrdem(ordemPagamento);

			List<BigDecimal> creditos = NumberUtils.installmentRound(valorCredito, referencias.size());

			int index = 0;
			for (Referencia referencia : referencias) {

				if (referencia.getValorCredito() == null) {
					referencia.setValorCredito(BigDecimal.ZERO);
				}

				ReferenciaOperacao referenciaOperacao = new ReferenciaOperacao(creditos.get(index), creditos.get(index),
						referencia, credito, LancamentoOperacaoEnum.CREDITO_RESGATE);

				referenciaOperacao.getOperacoesOcorrencias()
						.add(new ReferenciaOperacaoOcorrencia(
								"Lançamento gerado automaticamente através de pagamento(s)", DateUtils.today(),
								creditos.get(index), referenciaOperacao, Utils.getUser()));

				referencia.getReferenciasOperacoes().add(referenciaOperacao);

				referencia.setValorCredito(referencia.getValorCredito().add(creditos.get(index++)));
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new BOException(e.getMessage(), e);
		}
	}

	/**
	 * Método para provisionar um débito
	 * 
	 * @param debito
	 * @param valorPago
	 * @param dtBase
	 * @param observacao
	 * @return
	 * @throws BOException
	 */
	public CPROperacao provisionarDebito(CPRDebito debito, BigDecimal valorPago, Date dtBase, boolean automatico,
			String observacao) throws BOException {
		List<CPROperacao> operacoes = provisionarDebito(debito, valorPago, null, dtBase, automatico, observacao, "");

		if (CollectionUtils.isNotEmpty(operacoes)) {
			return operacoes.get(0);
		} else {
			return null;
		}
	}

	/**
	 * Método para provisionar um débito considerando o abatimento, caso exista.
	 * 
	 * @param debito
	 * @param valorPago
	 * @param valorAbatimento
	 * @param dtBase
	 * @param observacao
	 * @param observacaoAbatimento
	 * @throws BOException
	 */
	public List<CPROperacao> provisionarDebito(CPRDebito debito, BigDecimal valorPago, BigDecimal valorAbatimento,
			Date dtBase, boolean automatico, String observacao, String observacaoAbatimento) throws BOException {

		List<CPROperacao> operacoes = new ArrayList<CPROperacao>();

		if (debito.getCpr() == null) {
			throw new BOException("Não existe CPR vinculado ao débito", new NullPointerException());
		}

		if (valorAbatimento == null) {
			valorAbatimento = BigDecimal.ZERO;
		}

		if (valorPago == null) {
			valorPago = BigDecimal.ZERO;
		}

		if (NumberUtils.isGreaterThen(valorAbatimento.add(valorPago), debito.getSaldoReceber())) {
			throw new BOException("A soma do valor do abatimento com a valor "
					+ "a ser pago não pode ser superior ao saldo a receber");
		}

		try {

			CPROperacao provisao = null;

			if (NumberUtils.isGreaterThenZero(debito.getSaldoReceber())) {

				if (debito.getCprOperacoes() == null) {
					debito.setCprOperacoes(new ArrayList<CPROperacao>());
				}

				provisao = new CPROperacao();
				BeanUtils.copyProperties(provisao, debito);
				provisao.setId(0L);
				provisao.setDtBase(dtBase);
				provisao.setLancamentoOperacao(LancamentoOperacaoEnum.PROVISAO);
				provisao.setValor(valorPago);
				provisao.setCprDebito(debito);
				provisao.setObservacao(observacao);
				provisao.setAutomatico(automatico);
				provisao.setDtOperacao(DateUtils.now());

				debito.getCprOperacoes().add(provisao);
				debito.setSaldoReceber(debito.getSaldoReceber().subtract(provisao.getValor()));

				operacoes.add(provisao);
			}

			if (NumberUtils.isGreaterThenZero(valorAbatimento)) {
				operacoes.add(abaterDebito(debito, dtBase, automatico, valorAbatimento, observacaoAbatimento));
			}

			return operacoes;

		} catch (Exception e) {
			throw new BOException(e.getMessage(), e);
		}
	}

	/**
	 * Método para cancelar a operação
	 * 
	 * @param operacao
	 * @return
	 * @throws BOException
	 */
	public CPR cancelarOperacao(CPROperacao operacao) throws BOException {
		CPR cpr = operacao.getCpr();
		CPRDebito debito = operacao.getCprDebito();

		try {

			if (cpr == null) {
				throw new BOException("Não existe CPR vinculado a esta operação");
			}

			if (debito == null) {
				throw new BOException("Não existe débito vinculado a esta operação");
			}

			if (BooleanUtils.isTrue(operacao.getAutomatico())) {
				switch (operacao.getLancamentoOperacao()) {
				case ABATIMENTO:
					return cpr;

				case CREDITO_RESGATE:
					return cpr;

				default:
					break;
				}
			}

			if (LancamentoOperacaoEnum.CANCELADO.equals(operacao.getLancamentoOperacao())) {
				throw new BOException("Operação já cancelada");
			}

			/*
			 * Não apagar esta linha, ela carrega os itens desta coleção para
			 * evitar erros em alterações por CPR
			 */
			cpr.getCprDebitos().size();

			debito.setSaldoReceber(debito.getSaldoReceber().add(operacao.getValor()));
			operacao.setLancamentoOperacao(LancamentoOperacaoEnum.CANCELADO);

			return cpr;
		} catch (Exception e) {
			e.printStackTrace();
			throw new BOException(e.getMessage(), e);
		}

	}

	/**
	 * Método para realizar abatimento de um débito
	 * 
	 * @param debito
	 * @param dtBase
	 * @param valorAbatimento
	 * @param observacao
	 * @return
	 * @throws Exception
	 */
	public CPROperacao abaterDebito(CPRDebito debito, Date dtBase, boolean automatico, BigDecimal valorAbatimento,
			String observacao) throws Exception {

		try {

			if (debito.getCpr() == null) {
				throw new BOException("Não existe CPR vinculado ao débito");
			}

			if (NumberUtils.isGreaterThen(valorAbatimento, debito.getSaldoReceber())) {
				throw new BOException("O valor do abatimento não pode ser superior ao saldo a receber");
			}

			if (debito.getCprOperacoes() == null) {
				debito.setCprOperacoes(new ArrayList<CPROperacao>());
			}

			CPROperacao abatimento = new CPROperacao();
			BeanUtils.copyProperties(abatimento, debito);
			abatimento.setId(0L);
			abatimento.setDtBase(dtBase);
			abatimento.setDtOperacao(DateUtils.now());
			abatimento.setLancamentoOperacao(LancamentoOperacaoEnum.ABATIMENTO);
			abatimento.setValor(valorAbatimento);
			abatimento.setCprDebito(debito);
			abatimento.setObservacao(observacao);
			abatimento.setAutomatico(automatico);

			debito.setSaldoReceber(debito.getSaldoReceber().subtract(abatimento.getValor()));
			debito.getCprOperacoes().add(abatimento);

			return abatimento;

		} catch (Exception e) {
			e.printStackTrace();
			throw new BOException(e.getMessage(), e);
		}

	}

	/**
	 * Cancela as provisões vencidas de um débito, caso essa provisão esteja
	 * vinculada a uma ordem a mesma também é cancelada
	 * 
	 * @param debito
	 * @throws Exception
	 */
	public void cancelarProvisoesVencidas(CPRDebito debito) throws Exception {
		try {

			List<CPROrdem> ordensPagamento = CPRUtils.getOrdensDebitos(Arrays.asList(debito));

			for (CPROrdem ordemPagamento : ordensPagamento) {

				if (Arrays.asList(CPROrdemSituacaoEnum.ABERTO, CPROrdemSituacaoEnum.PENDENTE)
						.contains(ordemPagamento.getOrdemSituacao())
						&& ordemPagamento.getDtAgendamento().before(DateUtils.today())) {

					cancelarOrdemPagamento(ordemPagamento);

				}

			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new BOException(e.getMessage(), e);
		}
	}

	/**
	 * Cancela as provisões vencidas de uma coleção de débitos, caso a provisão
	 * esteja vinculada a uma ordem a mesma também é cancelada
	 * 
	 * @param debitos
	 * @throws Exception
	 */
	public void cancelarProvisoesVencidas(Collection<CPRDebito> debitos) throws Exception {
		try {
			for (CPRDebito debito : debitos) {
				cancelarProvisoesVencidas(debito);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new BOException(e.getMessage(), e);
		}
	}

	/**
	 * Remove as multas, juros e correções calculadas e não persistidas e
	 * adiciona novos registros de multa juros e correções baseados na data do
	 * agendamento
	 * 
	 * @param debitos
	 * @param dtAgendamento
	 * @return
	 * @throws BOException
	 */
	public List<CPRDebito> adicionarMJC(Collection<CPRDebito> debitos, Date dtAgendamento) throws BOException {

		Map<CPR, Map<LancamentoTipo, BigDecimal>> correcoes = new HashMap<CPR, Map<LancamentoTipo, BigDecimal>>();
		List<CPRDebito> correcoesDeb = new ArrayList<CPRDebito>();

		try {

			for (Iterator<CPRDebito> iterator = debitos.iterator(); iterator.hasNext();) {

				CPRDebito debito = iterator.next();

				if (debito.isAcrescimo() && debito.getId() == 0) {
					CPR cpr = debito.getCpr();
					cpr.getCprDebitos().remove(debito);
					iterator.remove();
				}
			}

			for (Iterator<CPRDebito> iterator = debitos.iterator(); iterator.hasNext();) {

				CPRDebito debito = iterator.next();
				Map<LancamentoTipo, BigDecimal> mapCorrecoes = null;

				for (int i = 0; i < 3; i++) {
					try {
						mapCorrecoes = calculoMonetarioBO.montarMapaAcrescimos(debito, dtAgendamento);
						break;
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				if (mapCorrecoes == null) {
					throw new BOException("Erro ao requisitar os dados de cáculo em correções.");
				} else {

					for (LancamentoTipo lancamento : mapCorrecoes.keySet()) {

						if (NumberUtils.isGreaterThenZero(mapCorrecoes.get(lancamento))) {

							CPR cpr = debito.getCpr();

							if (!correcoes.containsKey(cpr))
								correcoes.put(cpr, new HashMap<LancamentoTipo, BigDecimal>());

							if (!correcoes.get(cpr).containsKey(lancamento))
								correcoes.get(cpr).put(lancamento, BigDecimal.ZERO);

							correcoes.get(cpr).put(lancamento,
									correcoes.get(cpr).get(lancamento).add(mapCorrecoes.get(lancamento)));

						}
					}
				}
			}

			for (CPR cpr : correcoes.keySet()) {
				Map<LancamentoTipo, BigDecimal> mapLancamentos = correcoes.get(cpr);

				for (LancamentoTipo lancamento : mapLancamentos.keySet()) {
					CPRDebito correcao = cprBO.adicionarDebito(lancamento,
							mapLancamentos.get(lancamento).setScale(2, BigDecimal.ROUND_HALF_EVEN), cpr,
							cpr.getDtBase(), false, true);

					correcao.setDescricao(lancamento.getDescricao());
					correcoesDeb.add(correcao);
				}
			}

			debitos.addAll(correcoesDeb);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return correcoesDeb;
	}

	/**
	 * Adicionar custas adicionais a uma coleção de CPRs. O valor é distribuído
	 * pelo número de CPRs informados na coleção
	 * 
	 * @param cprs
	 * @param dtBase
	 * @param valorCusto
	 * @return
	 * @throws Exception
	 */
	public List<CPRDebito> calcularCustosAdicionais(Collection<CPR> cprs, Date dtBase, BigDecimal valorCusto)
			throws Exception {

		try {

			if (cprs == null)
				throw new BOException("Coleção de CPR está nula");

			List<CPRDebito> debitos = new ArrayList<CPRDebito>();

			List<BigDecimal> valorCustasAdicionais = NumberUtils.installmentRound(valorCusto, cprs.size());

			int i = 0;

			for (Iterator<CPR> iterator = cprs.iterator(); iterator.hasNext();) {
				CPR cpr = iterator.next();

				debitos.add(calcularCustosAdicionais(cpr, dtBase, valorCustasAdicionais.get(i)));
				i++;
			}

			return debitos;
		} catch (Exception e) {
			e.printStackTrace();
			throw new BOException(e.getMessage(), e);
		}

	}

	/**
	 * Adicionar custas adicionais ao CPR enviado.
	 * 
	 * @param cpr
	 * @param dtBase
	 * @param valor
	 * @return
	 * @throws Exception
	 */
	public CPRDebito calcularCustosAdicionais(CPR cpr, Date dtBase, BigDecimal valor) throws Exception {

		try {
			LancamentoTipo custasAdicionais = parametro.getReceita().getCustasAdicionais();

			CPRDebito custas = cprBO.adicionarDebito(custasAdicionais, valor, cpr, dtBase, false, true);
			custas.setDescricao(custasAdicionais.getDescricao());

			return custas;

		} catch (Exception e) {
			e.printStackTrace();
			throw new BOException(e.getMessage(), e);
		}
	}

	/**
	 * Cancela uma coleção de ordens de pagamento e suas operações. Caso a ordem
	 * esteja compensada, os créditos são excluídos
	 * 
	 * @param ordensPagamento
	 * @throws Exception
	 */
	public List<CPR> cancelarOrdensPagamento(Collection<? extends CPROrdem> ordensPagamento) throws BOException {
		try {
			List<CPR> cprs = new ArrayList<CPR>();

			for (CPROrdem cprOrdem : ordensPagamento) {
				cprs.addAll(cancelarOrdemPagamento(cprOrdem));
			}
			return cprs;
		} catch (Exception e) {
			e.printStackTrace();
			throw new BOException(e.getMessage(), e);
		}
	}

	/**
	 * Cancela uma ordem de pagamento e suas operações. Caso a ordem esteja
	 * compensada, os créditos são excluídos
	 * 
	 * @param ordemPagamento
	 * @throws Exception
	 */
	public List<CPR> cancelarOrdemPagamento(CPROrdem ordemPagamento) throws BOException {
		BigDecimal valorCredito = BigDecimal.ZERO;

		try {
			if (CPROrdemSituacaoEnum.CANCELADO.equals(ordemPagamento.getOrdemSituacao())) {
				return new ArrayList<CPR>(CPRUtils.getCpr(ordemPagamento.getCprOperacoes()));
			}

			for (Iterator<CPROperacao> iterator = ordemPagamento.getCprOperacoes().iterator(); iterator.hasNext();) {

				CPROperacao operacao = iterator.next();
				operacao.setCprCredito(null);
				operacao.setDtCancelamento(DateUtils.today());

				CPRDebito debito = operacao.getCprDebito();

				if (BooleanUtils.isTrue(debito.isAcrescimo())) {
					CPR cpr = operacao.getCpr();
					debito.getCprOperacoes().remove(operacao);
					debito.setValor(debito.getValor().subtract(operacao.getValor()));

					if (!NumberUtils.isGreaterThenZero(debito.getValor())) {
						cpr.getCprDebitos().remove(debito);
						operacao.setCprOrdem(null);
						iterator.remove();
					}

				} else {
					if (LancamentoOperacaoEnum.CREDITO_REFERENCIA.equals(operacao.getLancamentoOperacao())) {
						valorCredito = valorCredito.add(operacao.getValor());
						operacao.setLancamentoOperacao(LancamentoOperacaoEnum.CANCELADO);
					} else if (!operacao.getLancamentoOperacao().equals(LancamentoOperacaoEnum.CANCELADO)) {
						cancelarOperacao(operacao);
					}
				}
			}

			estornarCreditoReferencia(ordemPagamento, valorCredito);

			if (CPROrdemSituacaoEnum.COMPENSADO.equals(ordemPagamento.getOrdemSituacao())) {

				if (CollectionUtils.isNotEmpty(ordemPagamento.getCprCreditos())) {
					// Carrega os dados da lista caso não estejam preenchidos
					ordemPagamento.getCprCreditos().size();

					for (CPRCredito credito : ordemPagamento.getCprCreditos()) {
						estornarCreditoReferencia(credito, valorCredito);
					}
					// Remove os créditos
					ordemPagamento.getCprCreditos().clear();
				}
			}

			ordemPagamento.setOrdemSituacao(CPROrdemSituacaoEnum.CANCELADO);
			return new ArrayList<CPR>(CPRUtils.getCpr(ordemPagamento.getCprOperacoes()));

		} catch (Exception e) {
			e.printStackTrace();
			throw new BOException(e.getMessage(), e);
		}
	}

	/**
	 * Estorna o valor de crédito para as referencias da ordem que está sendo
	 * cancelada.
	 * 
	 * @param ordemPagamento
	 * @param valorCredito
	 * @throws BOException
	 * @version 1.0
	 */
	private void estornarCreditoReferencia(CPROrdem ordemPagamento, BigDecimal valorCredito) throws BOException {
		if (NumberUtils.isGreaterThenZero(valorCredito)) {

			List<Referencia> referencias = CPRUtils.getReferenciasOrdem(ordemPagamento, true);

			Map<Object, BigDecimal> referenciasValores = new HashMap<Object, BigDecimal>();

			for (Referencia referencia : referencias) {
				BigDecimal valorReferencia = referencia.getValorCredito();
				referenciasValores.put(referencia,
						NumberUtils.isGreaterThenZero(valorReferencia) ? valorReferencia : BigDecimal.ZERO);
			}

			Map<Object, BigDecimal> valoresDistribuidos = NumberUtils.ratingValues(referenciasValores, valorCredito,
					true);

			for (Referencia referencia : referencias) {
				BigDecimal valor = valoresDistribuidos.get(referencia);
				BigDecimal valorReferencia = referencia.getValorCredito() != null ? referencia.getValorCredito()
						: BigDecimal.ZERO;
				referencia.setValorCredito(valorReferencia.subtract(valor));
				if (referencia.getValorCredito().compareTo(BigDecimal.ZERO) < 0) {
					throw new BOException("Não é possível negativar o saldo de um imóvel");
				}
			}
		}
	}

	private void estornarCreditoReferencia(CPRCredito credito, BigDecimal estorno) throws BOException {

		List<Referencia> referencias = CPRUtils.getReferenciasOrdem(credito.getCprOrdem(), true);

		List<ReferenciaOperacao> operacoes = referencias.stream().map(Referencia::getReferenciasOperacoes)
				.flatMap(r -> r.stream()).filter(r -> credito.equals(r.getCprCredito())).collect(Collectors.toList());

		operacoes.stream().forEach(o -> o.setCprCredito(null));
		BigDecimal saldo = operacoes.stream().map(ReferenciaOperacao::getSaldo).reduce(BigDecimal.ZERO,
				BigDecimal::add);

		if (saldo.compareTo(estorno) < 0) {
			throw new BOException("Não é possível negativar o saldo de um imóvel");
		} else {
			Map<Object, BigDecimal> valores = new HashMap<Object, BigDecimal>();

			operacoes.stream().forEach(o -> valores.put(o, o.getSaldo()));

			Map<Object, BigDecimal> valoresDistribuidos = NumberUtils.ratingValues(valores, estorno);

			for (Entry<Object, BigDecimal> valor : valoresDistribuidos.entrySet()) {

				ReferenciaOperacao operacao = (ReferenciaOperacao) valor.getKey();

				operacao.setSaldo(operacao.getSaldo().subtract(valor.getValue()));

				operacao.getOperacoesOcorrencias().add(new ReferenciaOperacaoOcorrencia("Pagamento cancelado.",
						DateUtils.today(), valor.getValue(), operacao, Utils.getUser()));
			}
		}
	}

	/**
	 * Popula uma ordem de pagamento com os dados fornecidos.
	 * 
	 * @param operacoes
	 *            Lista das quais será extraído o valor da ordem.
	 * @param ordemPagamento
	 *            Ordem a ser populada
	 * @param dtAgendamento
	 *            Data de vencimento da ordem.
	 * @param parceiro
	 * @param referencia
	 * @param agrupado
	 * 
	 * @throws BOException
	 * @version 1.0
	 */
	public void popularOrdem(Collection<CPROperacao> operacoes, CPROrdem ordemPagamento, Date dtAgendamento,
			Parceiro parceiro, Referencia referencia, boolean agrupado) throws BOException {
		try {
			BigDecimal valorTitulo = BigDecimal.ZERO;

			if (referencia == null && !agrupado)
				throw new BOException("Operações agrupadas sem referência");

			if (ordemPagamento.getCprOperacoes() == null) {
				ordemPagamento.setCprOperacoes(new ArrayList<CPROperacao>());
			}

			for (CPROperacao operacao : operacoes) {
				if (LancamentoOperacaoEnum.PROVISAO.equals(operacao.getLancamentoOperacao())) {
					valorTitulo = valorTitulo.add(operacao.getValor());
				}

				ordemPagamento.getCprOperacoes().add(operacao);
				operacao.setCprOrdem(ordemPagamento);

			}

			if (ordemPagamento instanceof CPRTitulo) {
				CPRTitulo cprTitulo = (CPRTitulo) ordemPagamento;
				if (cprTitulo.getEndereco() == null) {
					cprTitulo.setEndereco(parceiroBO.getEndereco(referencia, parceiro));
				}
			}

			ordemPagamento.setParceiro(parceiro);
			ordemPagamento.setDtAgendamento(dtAgendamento);
			ordemPagamento.setDtOperacao(DateUtils.now());
			ordemPagamento.setValor(valorTitulo);
			ordemPagamento.setReferencia(referencia);
			ordemPagamento.setAgrupado(agrupado);

			if (NumberUtils.isGreaterThenZero(ordemPagamento.getValor())) {
				ordemPagamento.setOrdemSituacao(CPROrdemSituacaoEnum.ABERTO);
			} else {
				ordemPagamento.setOrdemSituacao(CPROrdemSituacaoEnum.COMPENSADO);
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new BOException(e.getMessage(), e);
		}
	}

	/**
	 * Caso a data seja um feriado já cadastrado, o sistema irá procurar a
	 * proxima data util.
	 * 
	 * @param dtAgendamento
	 * @return
	 * @version 1.1
	 */
	public Date getProximaDataUtil(Date dtAgendamento) {

		while (AtualizacaoMonetariaHelper.isFeriado(dtAgendamento, feriados)) {
			dtAgendamento = DateUtils.addDays(dtAgendamento, 1);
		}

		return dtAgendamento;
	}
}
