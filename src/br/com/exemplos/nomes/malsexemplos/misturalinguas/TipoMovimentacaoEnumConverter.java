package br.com.exemplos.nomes.malsexemplos.misturalinguas;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import br.com.hojeti.enumerator.MovimentacaoTipoEnum;

@Converter(autoApply = true)
public class TipoMovimentacaoEnumConverter
		implements
			AttributeConverter<MovimentacaoTipoEnum, Integer> {

	@Override
	public Integer convertToDatabaseColumn(
			MovimentacaoTipoEnum tipoMovimentacaoEnum) {
		return tipoMovimentacaoEnum != null
				? tipoMovimentacaoEnum.getIndex()
				: 0;
	}

	@Override
	public MovimentacaoTipoEnum convertToEntityAttribute(Integer index) {
		return index != null ? MovimentacaoTipoEnum.getEnum(index) : null;
	}
}