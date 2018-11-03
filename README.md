# mixy

A library for bytebuddy advice interceptors.

## Usage

To use mixy you will need to annotate a class you want to mixin to
another with the Mixin annotation. Example code below. Take a look at the
[advice javadocs](http://bytebuddy.net/javadoc/1.8.12/net/bytebuddy/asm/Advice.html) for more advanced usage.

```java

package com.clubobsidian.mixintest;

import java.util.logging.Level;

import org.bukkit.Bukkit;

import com.clubobsidian.mixy.annotation.Mixin;

import net.bytebuddy.asm.Advice;

@Mixin("org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer")
public class CraftPlayerMessageMixin {

	@Advice.OnMethodEnter
	public static void sendMessage()
	{
		Bukkit.getServer().getLogger().log(Level.INFO, "MIXIN");
	}
}


```