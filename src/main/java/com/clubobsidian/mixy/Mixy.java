/*  
   Copyright 2018 Club Obsidian and contributors.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.clubobsidian.mixy;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarFile;

import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;

import com.clubobsidian.mixy.annotation.Mixin;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.matcher.ElementMatchers;

public class Mixy {

	private static final Logger logger = LoggerFactory.getLogger(Mixy.class);
	
	public static void main(String[] args)
	{	
		File mixyLogFile = new File("mixy.log");
		if(mixyLogFile.exists())
			mixyLogFile.delete();
		
		BasicConfigurator.configure();
		MixyArgs mixyArgs = new MixyArgs();
		
		JCommander.newBuilder()
		.acceptUnknownOptions(true)
		.addObject(mixyArgs)
		.build()
		.parse(args);
		
		Mixy mixy = new Mixy();
		mixy.bootstrap();
		if(mixyArgs.jar != null)
		{
			File jarFile = new File(mixyArgs.jar);
			if(mixy.loadMainJar(jarFile))
			{
				if(mixy.loadMixins())
				{
					mixy.runMainJar();
				}
			}
		}
		else
		{
			Mixy.getLogger().error("You must specify a jar");
		}
	}
	
	private MixyClassLoader loader;
	private Instrumentation inst;
	private File mainJar;
	public Mixy()
	{

	}

	public boolean bootstrap()
	{
		try
		{
			this.inst = ByteBuddyAgent.install();
			return true;
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		return false;
	}

	public boolean loadMainJar(File jarFile)
	{
		if(jarFile.exists())
		{
			boolean loaded = this.loadJar(jarFile);
			if(loaded)
			{
				this.mainJar = jarFile;
			}
			return loaded;
		}
		else
		{
			Mixy.getLogger().error("Specified jar file does not exist");
		}
		return false;
	}
	
	public boolean loadMixins()
	{
		File mixinsFolder = new File("mixins");
		if(!mixinsFolder.exists())
		{
			mixinsFolder.mkdir();
		}
		if(mixinsFolder.isDirectory())
		{
			for(File file : mixinsFolder.listFiles())
			{
				this.loadJar(file);
				try 
				{
					JarFile jar = new JarFile(file);
					jar.stream().forEach(entry -> 
					{ 
						String name = entry.getName();
						if(name.endsWith(".class"))
						{
							name = name.replace("/", ".");
							name = name.replace("\\", ".");
							name = name.replace(".class", "");
							System.out.println(name);
							try 
							{
								Class<?> interceptorClass = this.loader.loadClass(name);
								Mixin mixin = interceptorClass.getDeclaredAnnotation(Mixin.class);
								if(mixin != null)
								{
									for(Method m : interceptorClass.getDeclaredMethods())
									{
										if(m.getAnnotations().length > 0)
										{
											Mixy.getLogger().info("Adding interceptor for " + m.getName());
											this.addInterceptor(mixin.value(), m, interceptorClass);
										}
									}
								}
							} 
							catch (ClassNotFoundException e) 
							{
								e.printStackTrace();
							}
						}
					});
					jar.close();
				} 
				catch (IOException e) 
				{
					e.printStackTrace();
					return false;
				}
				
			}
			
			return true;
		}
		return false;
	}

	public boolean runMainJar()
	{
		this.runJar(this.mainJar);
		return false;
	}

	private void addInterceptor(String classToMixinTo, Method interceptorMethod, Class<?> interceptorClass)
	{
		new AgentBuilder.Default()
		.type(ElementMatchers.named(classToMixinTo))
		.transform(
		new AgentBuilder.Transformer.ForAdvice()
		.include(this.loader)
		.advice(ElementMatchers.named(interceptorMethod.getName()), interceptorClass.getName())
		)
		.installOn(this.inst);
	}
	
	private boolean runJar(File file)
	{
		Thread.currentThread().setContextClassLoader(this.loader);
		
		JarFile jar = null;
		try 
		{
			jar = new JarFile(file);
			String mainClass = jar.getManifest().getMainAttributes().getValue("Main-Class");
			Class<?> cl = this.loader.loadClass(mainClass);
			Method m = cl.getMethod("main", String[].class);
			m.invoke(null, new Object[] {new String[0]});
			jar.close();
			return true;
		} 
		catch (IOException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException e) 
		{
			e.printStackTrace();
		}
		finally
		{
			try 
			{
				jar.close();
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}
		}
		
		return false;
	}
	
	private boolean loadJar(File file)
	{
		if(this.loader == null)
		{
			try 
			{
				URL url = file.toURI().toURL();
				this.loader = new MixyClassLoader(new URL[]{url});
			} 
			catch (MalformedURLException e) 
			{
				e.printStackTrace();
				return false;
			}
			return true;
		}
		
		try 
		{
			this.loader.addURL(file.toURI().toURL());
		} 
		catch (MalformedURLException e) 
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public static Logger getLogger()
	{
		return Mixy.logger;
	}
}