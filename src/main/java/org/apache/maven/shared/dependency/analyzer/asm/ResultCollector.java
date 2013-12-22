package org.apache.maven.shared.dependency.analyzer.asm;

import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Kristian Rosenvold
 */
public class ResultCollector {

    private final Set<String> classes = new HashSet<String>();
    ;

    public Set<String> getDependencies()
    {
        return classes;
    }

    public void addName( String name )
    {
        if ( name == null )
        {
            return;
        }

        // decode arrays
        if ( name.startsWith( "[L" ) && name.endsWith( ";" ) )
        {
            name = name.substring( 2, name.length() - 1 );
        }

        // decode internal representation
        name = name.replace( '/', '.' );

        classes.add( name );
    }

    void addDesc(final String desc)
    {
        addType( Type.getType(desc) );
    }

    void addType(final Type t)
    {
        switch ( t.getSort() )
        {
            case Type.ARRAY:
                addType( t.getElementType() );
                break;

            case Type.OBJECT:
                addName( t.getClassName().replace( '.', '/' ) );
                break;
        }
    }

    public void add(String name) {
        classes.add( name);
    }
}
