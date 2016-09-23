package br.com.exemplos.nomes.bonsexemplos.metodos;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;

import br.com.hojeti.dao.ParametroDao;
import br.com.hojeti.enumerator.LancamentoOperacaoEnum;
import br.com.hojeti.exception.BOException;
import br.com.hojeti.model.CPR;
import br.com.hojeti.model.CPRCredito;
import br.com.hojeti.model.CPRDebito;
import br.com.hojeti.model.CPROperacao;
import br.com.hojeti.model.CPROrdem;
import br.com.hojeti.model.LancamentoPre;
import br.com.hojeti.model.LancamentoTipo;
import br.com.hojeti.model.Parametro;
import br.com.hojeti.model.Parceiro;
import br.com.hojeti.model.ReceitaCalculada;
import br.com.hojeti.model.Referencia;
import br.com.hojeti.utils.DateUtils;
import br.com.hojeti.utils.NumberUtils;

public class CPRBO implements Serializable {

	private static final long serialVersionUID = 4446478778902639751L;

	/*
	 * DAOs
	 */
	private ParametroDao parametroDao = new ParametroDao();

	private Parametro parametro;

	public CPRBO() {
		try {
			this.parametro = parametroDao.carregarParametro();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public CPR criarCPR(Parceiro parceiro, Date dtVencimento, Referencia referencia) {

		CPR cpr = new CPR();

		cpr.setReferencia(referencia);
		cpr.setValorOriginal(BigDecimal.ZERO);
		cpr.setDtBase(dtVencimento);
		cpr.setMesCompetencia(DateUtils.getMonth(dtVencimento));
		cpr.setAnoCompetencia(DateUtils.getYear(dtVencimento));
		cpr.setAgrupado(BooleanUtils.isTrue(parceiro.isAgrupaTitulo()));
		cpr.setParceiro(parceiro);
		cpr.setCprDebitos(new ArrayList<CPRDebito>());

		return cpr;
	}

	public CPR criarCPR(Parceiro parceiro, Date dtVencimento, Referencia referencia,
			ReceitaCalculada receitaCalculada) {

		CPR cpr = criarCPR(parceiro, dtVencimento, referencia);
		cpr.setAgrupado(BooleanUtils.isTrue(parceiro.isAgrupaTitulo()));
		cpr.setReceitaCalculada(receitaCalculada);
		return cpr;
	}

	/**
	 * Cria um crédito para uma determinada ordem de pagamento;
	 * 
	 * @param ordemPagamento
	 * @param valorCredito
	 * @param dtPagamento
	 * @param dtCompensacao
	 * @return
	 * @version 1.0.1
	 */
	public CPRCredito criarCredito(CPROrdem ordemPagamento, BigDecimal valorCredito, Date dtPagamento,
			Date dtCompensacao) throws BOException {

		try {

			CPRCredito credito = new CPRCredito();
			credito.setValor(valorCredito);
			credito.setCprOrdem(ordemPagamento);
			credito.setDtPagamento(dtPagamento);
			credito.setDtCompensacao(dtCompensacao);
			credito.setCprOrdem(ordemPagamento);

			// Adicionar crédito
			ordemPagamento.getCprCreditos().add(credito);

			if (CollectionUtils.isNotEmpty(ordemPagamento.getCprOperacoes())) {
				credito.setCprOperacoes(ordemPagamento.getCprOperacoes());
				for (CPROperacao operacao : ordemPagamento.getCprOperacoes()) {
					operacao.setCprCredito(credito);
				}
			}

			return credito;

		} catch (Exception e) {
			throw new BOException(e.getMessage(), e);
		}

	}

	/**
	 * Adicionar um {@link CPRDebito} a um {@link CPR}.
	 * 
	 * @param lancamento
	 *            Tipo de lançamento do débito
	 * @param valor
	 *            Valor do débito
	 * @param cpr
	 *            {@link CPR} agrupador de um conjunto de {@link CPRDebito}
	 * @param dtBase
	 *            Data base de agendamento
	 * @param avulso
	 *            Débito é proveniente de um lançamento avulso
	 * @param acrescimo
	 *            Débito é proveniente de um acréscimo
	 * @return {@link CPRDebito} gerado.
	 * @throws BOException
	 * @version 1.0
	 */
	public CPRDebito adicionarDebito(LancamentoTipo lancamento, BigDecimal valor, CPR cpr, Date dtBase, boolean avulso,
			boolean acrescimo) throws BOException {

		if (cpr == null)
			throw new BOException("CPR nulo!");

		CPRDebito debito = new CPRDebito();

		debito.setAvulso(avulso);
		debito.setCpr(cpr);
		debito.setCprOperacoes(new ArrayList<CPROperacao>());
		debito.setDtBase(dtBase);
		debito.setDtOperacao(DateUtils.now());
		debito.setInadimplenciaSituacao(parametro.getReceita().getInadimplenciaSituacaoPadrao());
		debito.setLancamentoOperacao(LancamentoOperacaoEnum.DEBITO);
		debito.setLancamentosPre(new ArrayList<LancamentoPre>());
		debito.setLancamentoTipo(lancamento);
		debito.setSaldoReceber(valor);
		debito.setValor(valor);
		debito.setAcrescimo(acrescimo);
		debito.setDescricao(lancamento != null ? lancamento.getDescricao() : null);

		if (!acrescimo) {
			cpr.setValorOriginal(cpr.getValorOriginal().add(valor));
		}

		if (cpr.getCprDebitos() == null) {
			cpr.setCprDebitos(new ArrayList<CPRDebito>());
		}

		if (!cpr.getCprDebitos().contains(debito)) {
			cpr.getCprDebitos().add(debito);
		}
		return debito;
	}

	public List<CPRDebito> criarCPRDebitos(CPR cpr, Map<LancamentoTipo, BigDecimal> lancamentos, Date dtBase,
			boolean avulso, boolean incidirAcrescimo) throws Exception {
		List<CPRDebito> debitos = new ArrayList<CPRDebito>();

		for (LancamentoTipo lancamentoTipo : lancamentos.keySet()) {
			BigDecimal valor = lancamentos.get(lancamentoTipo);

			if (NumberUtils.isNotGreaterThenZero(valor))
				continue;

			CPRDebito cprDebito = adicionarDebito(lancamentoTipo, valor, cpr, dtBase, avulso, incidirAcrescimo);
			cprDebito.setDescricao(lancamentoTipo.getDescricao());

			if (cpr.getCprDebitos() == null)
				cpr.setCprDebitos(new ArrayList<CPRDebito>());

			cpr.getCprDebitos().add(cprDebito);
			debitos.add(cprDebito);
		}

		return debitos;
	}

}
