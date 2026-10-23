package thaumcraft.common.golems.seals;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.golems.EnumGolemTrait;
public final class SealBreakerAdvanced extends SealBreaker {
 private final SealToggle[] advanced={new SealToggle(true,"pmeta","golem.prop.meta"),new SealToggle(false,"psilk","golem.prop.silk")};
 public String getKey(){return "thaumcraft:breaker_advanced";}public int getFilterSize(){return 9;}public ResourceLocation getSealIcon(){return ResourceLocation.parse("thaumcraft:item/seals/seal_breaker_advanced");}
 public SealToggle[] getToggles(){return advanced;}public EnumGolemTrait[] getRequiredTags(){return new EnumGolemTrait[]{EnumGolemTrait.BREAKER,EnumGolemTrait.SMART};}
}
