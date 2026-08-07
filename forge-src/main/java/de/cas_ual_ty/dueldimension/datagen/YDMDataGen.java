package de.cas_ual_ty.dueldimension.datagen;

import de.cas_ual_ty.dueldimension.DuelDimension;
import de.cas_ual_ty.dueldimension.card.CardSleevesType;
import de.cas_ual_ty.dueldimension.clientutil.ImageHandler;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.io.IOException;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class YDMDataGen
{
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event)
    {
        try
        {
            ImageHandler.createCustomSleevesImages(CardSleevesType.DUELING_MC, "png");
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
        
        DataGenerator generator = event.getGenerator();
        generator.addProvider(event.includeClient(), new YDMItemModels(generator, DuelDimension.MOD_ID, event.getExistingFileHelper()));
    }
}