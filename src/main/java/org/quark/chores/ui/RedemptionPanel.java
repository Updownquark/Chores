package org.quark.chores.ui;

import java.awt.Color;

import javax.swing.BorderFactory;

import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.StringUtils;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;
import org.quark.chores.entities.PointResource;

public class RedemptionPanel {
	private final ChoresUI theUI;

	public RedemptionPanel(ChoresUI ui) {
		theUI = ui;
	}

	public void addPanel(PanelPopulator<?, ?> panel) {
		panel.addTable(theUI.getPointResources().getValues(), table -> {
			table.fill().fillV().dragSourceRow(null).dragAcceptRow(null)//
					.withNameColumn(PointResource::getName, PointResource::setName, true, null)//
					.withColumn("Rate", double.class, pr -> pr == null ? 0.0 : pr.getRate(), col -> col.withMutation(mut -> {
						mut.mutateAttribute(PointResource::setRate).asText(SpinnerFormat.doubleFormat("0.###", 1))
								.filterAccept((__, rate) -> {
									if (rate == 0.0) {
										return "Rate can be positive or negative, but not zero";
									}
									return null;
								});
					}).decorate((cell, deco) -> deco
							.withBorder(BorderFactory.createLineBorder(cell.getCellValue() > 0 ? Color.red : Color.green)))//
							.withHeaderTooltip("The number of units of this resource that can be bought for a point"))//
					.withColumn("Unit", String.class, pr -> pr == null ? "" : pr.getUnit(), col -> col.withMutation(mut -> {
						mut.mutateAttribute(PointResource::setUnit).asText(Format.TEXT);
					}))//
					.withSelection(theUI.getSelectedPointResource(), false)//
					.withAdd(() -> {
						return theUI.getPointResources().create()//
								.with(PointResource::getName,
										StringUtils.getNewItemName(theUI.getPointResources().getValues(), PointResource::getName,
												"Resource", StringUtils.PAREN_DUPLICATES))//
								.with(PointResource::getRate, 1.0)//
								.with(PointResource::getUnit, "")//
								.create().get();
					}, null)
					.withRemove(null, action -> action.confirmForItems("Delete Redemption Resources", "Permanently delete", null, true))//
			;
		});
	}
}
