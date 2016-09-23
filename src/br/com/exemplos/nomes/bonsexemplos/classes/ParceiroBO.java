package br.com.exemplos.nomes.bonsexemplos.classes;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;

import br.com.hojeti.dao.ParametroDao;
import br.com.hojeti.dao.ParceiroDao;
import br.com.hojeti.enumerator.TelefoneEnum;
import br.com.hojeti.exception.BOException;
import br.com.hojeti.exception.DaoException;
import br.com.hojeti.model.Imovel;
import br.com.hojeti.model.Parametro;
import br.com.hojeti.model.Parceiro;
import br.com.hojeti.model.ParceiroEmail;
import br.com.hojeti.model.ParceiroEndereco;
import br.com.hojeti.model.ParceiroTelefone;
import br.com.hojeti.model.Referencia;
import br.com.hojeti.utils.EmailUtils;

/**
 * @author Hoje Tecnologia
 * @version 1.1
 *
 */
public class ParceiroBO {

	ParametroDao parametroDao = new ParametroDao();
	Parametro parametro;
	private ParceiroDao parceiroDao = new ParceiroDao();

	public ParceiroBO() {
		try {

			parametro = parametroDao.carregarParametro();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<String> getEmailPadrao(Parceiro parceiro) {
		return getEmailPadrao(parceiro, false);
	}

	public List<String> getEmailPadrao(Parceiro parceiro, boolean todos) {
		List<ParceiroEmail> emails = parceiro.getEmails();
		List<String> emailsPadroes = new ArrayList<String>();

		if (CollectionUtils.isNotEmpty(emails)) {
			for (ParceiroEmail parceiroEmail : emails) {
				if (parceiroEmail != null) {
					if (BooleanUtils.isTrue(parceiroEmail.getPadrao())) {
						String email = parceiroEmail.getEndereco();
						if (EmailUtils.isValidEmail(email) || todos) {
							emailsPadroes.add(email);
						}
					}
				}
			}
		}

		return emailsPadroes;
	}

	/**
	 * Retorna o endereço de uso para entrega de títulos. Caso não possua
	 * referência, o endereço é retirado do parceiro. Caso o parceiro não possua
	 * endereços cadastrados o endereço retornado é o endereço cadastrado em
	 * parâmetros
	 * 
	 * @param referencia
	 * @param parceiro
	 * @return
	 * @throws BOException
	 * @version 1.0.1
	 */
	public ParceiroEndereco getEndereco(Referencia referencia, Parceiro parceiro)
			throws BOException {
		ParceiroEndereco endereco = null;

		if (referencia instanceof Imovel) {
			Imovel imovel = (Imovel) referencia;
			if (imovel.getEndereco() != null) {
				endereco = imovel.getEndereco();
			}
		}

		if (endereco == null) {
			if (parceiro != null) {
				endereco = getEnderecoPadrao(parceiro);
			}
		}

		if (endereco == null) {
			if (parametro != null) {
				endereco = parametro.getEmpresa().getEndereco();
			}
		}

		return endereco;
	}

	public ParceiroEndereco getEnderecoPadrao(Parceiro parceiro) {
		List<ParceiroEndereco> enderecos = parceiro.getEnderecos();

		if (CollectionUtils.isNotEmpty(enderecos)) {
			for (ParceiroEndereco parceiroEndereco : enderecos) {
				if (parceiroEndereco.getPadrao() != null
						&& parceiroEndereco.getPadrao())
					return parceiroEndereco;
			}
		}

		return null;
	}

	public List<ParceiroTelefone> getTelefonePorTipo(Parceiro parceiro,
			TelefoneEnum tipo) {
		List<ParceiroTelefone> telefonesTipo = new ArrayList<ParceiroTelefone>();

		if (CollectionUtils.isNotEmpty(parceiro.getTelefones())) {
			for (ParceiroTelefone parceiroTelefone : parceiro.getTelefones()) {
				if (tipo == parceiroTelefone.getTipoTelefone())
					telefonesTipo.add(parceiroTelefone);
			}
		}

		return telefonesTipo;
	}

	public List<ParceiroTelefone> getTelefoneNotificacao(
			List<Parceiro> parceiros) {
		List<ParceiroTelefone> telefones = new ArrayList<ParceiroTelefone>();
		if (CollectionUtils.isNotEmpty(parceiros)) {
			for (Parceiro parceiro : parceiros) {
				telefones.addAll(getTelefoneNotificacao(parceiro));
			}
		}
		return telefones;
	}

	public List<ParceiroTelefone> getTelefoneNotificacao(Parceiro parceiro) {
		List<ParceiroTelefone> telefones = new ArrayList<ParceiroTelefone>();

		if (CollectionUtils.isNotEmpty(parceiro.getTelefones())) {
			for (ParceiroTelefone parceiroTelefone : parceiro.getTelefones()) {
				if (parceiroTelefone.getTipoTelefone() == TelefoneEnum.CELULAR
						&& BooleanUtils.isTrue(parceiroTelefone
								.getNotificacao())) {

					telefones.add(parceiroTelefone);
				}
			}
		}

		return telefones;
	}

	/**
	 * Obtém os Dependentes segundo o nome {@link Parceiro} informado.
	 * 
	 * @param parceiro
	 * @return
	 * @throws BOException
	 * @version 1.0
	 */
	public List<Parceiro> getDependentes(final Parceiro parceiro)
			throws BOException {
		try {
			return parceiroDao.listarDependentes(parceiro);
		} catch (DaoException e) {
			throw new BOException(e.getMessage(), e);
		}
	}
}