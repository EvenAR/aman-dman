# AMAN/DMAN Euroscope Bridge 

EuroScope plugin that allows EuroScope and the Java-application to exchange information.

## Development

If you need to make changes to the EuroScope bridge C++ plugin you should use [Visual Studio Community](https://visualstudio.microsoft.com/vs/community/).

**Debugging with Visual Studio:**

1. Open the project in the `euroscope-bridge` directory (double-click `Aman.sln`).
2. In **Solution Explorer**, right-click on **Aman** → **Properties**.
3. Under the **Debugging** page, navigate to your installation directory of `EuroScope.exe` and apply the changes.
4. Run **Local Windows Debugger**.  
   If everything works correctly, a `.dll` file is written to `euroscope-bridge\Debug`.
5. Load the `.dll` plugin in EuroScope.