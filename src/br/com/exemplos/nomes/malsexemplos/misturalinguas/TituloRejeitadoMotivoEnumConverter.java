package br.com.exemplos.nomes.malsexemplos.misturalinguas;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import br.com.hojeti.enumerator.TituloRejeitadoMotivoEnum;

@Converter(autoApply = true)
public class TituloRejeitadoMotivoEnumConverter implements
		AttributeConverter<TituloRejeitadoMotivoEnum, Integer> {

	@Override
	public Integer convertToDatabaseColumn(
			TituloRejeitadoMotivoEnum tituloRejeitadoMotivoEnum) {
		return tituloRejeitadoMotivoEnum != null ? tituloRejeitadoMotivoEnum
				.getIndex() : 0;
	}

	@Override
	public TituloRejeitadoMotivoEnum convertToEntityAttribute(Integer index) {
		return index != null ? TituloRejeitadoMotivoEnum.getEnum(index) : null;
	}

}
