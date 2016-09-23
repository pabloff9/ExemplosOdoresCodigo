package br.com.exemplos.nomes.bonsexemplos.nomecompletos;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import br.com.hojeti.enumerator.TituloRejeitadoSituacaoEnum;

@Converter(autoApply = true)
public class TituloRejeitadoSituacaoEnumConverter implements
		AttributeConverter<TituloRejeitadoSituacaoEnum, Integer> {

	@Override
	public Integer convertToDatabaseColumn(
			TituloRejeitadoSituacaoEnum tituloRejeitadoSituacaoEnum) {
		return tituloRejeitadoSituacaoEnum != null ? tituloRejeitadoSituacaoEnum
				.getIndex() : null;
	}

	@Override
	public TituloRejeitadoSituacaoEnum convertToEntityAttribute(Integer index) {
		return index != null ? TituloRejeitadoSituacaoEnum.getEnum(index)
				: null;
	}

}
