#version 150
uniform float EffectTime;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
in vec4 parameters;
in vec2 uv;
in float viewDistance;
out vec4 fragColor;

float hash(vec2 p) {
    vec3 p3=fract(vec3(p.xyx)*.1031);
    p3+=dot(p3,p3.yzx+33.33);
    return fract((p3.x+p3.y)*p3.z);
}
float noise(vec2 p) {
    vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+1.0),f.x),f.y);
}
float flow(vec2 p) {return .62*noise(p)+.27*noise(p*2.03+7.1)+.11*noise(p*4.11-3.7);}
vec3 fireColor(float temperature) {
    vec3 color=mix(vec3(.24,.003,.006),vec3(.88,.035,.005),smoothstep(.05,.38,temperature));
    color=mix(color,vec3(1,.38,.015),smoothstep(.38,.7,temperature));
    return mix(color,vec3(1,.85,.19),smoothstep(.58,.85,temperature));
}
void main() {
    float mode=parameters.r,seed=parameters.g*29.0,heat=parameters.b;
    float time=EffectTime;
    vec3 color;float alpha;
    if(mode>.85) {
        // Irregular soot, not a coal-block texture: radial falloff has no square boundary.
        vec2 q=uv;
        float n=flow(q*2.5);
        float edge=smoothstep(.17,.42,n);
        float cracks=1.0-smoothstep(.018,.065,abs(noise(q*15.0)-.51));
        float ember=cracks*heat*heat*(.65+.35*sin(time*3.0+seed+q.x*9.0));
        color=mix(vec3(.065,.042,.028),vec3(.17,.12,.075),n);
        color=mix(color,vec3(.9,.18,.012),ember*.7);
        alpha=edge*(.58+.3*n)*parameters.a;
    } else if(mode>.75) {
        color=vec3(.045,.028,.018);
        alpha=sin(uv.x*3.14159)*parameters.a;
    } else if(mode>.08 && mode<.2) {
        // Integrate a moving density field through an ellipsoid. The proxy edge has zero density.
        // Eight bounded samples; no persistent simulation, textures, or polygon-shaped silhouette.
        vec2 q=(uv-.5)*2.0;
        float curl=flow(vec2(q.y*3.5-time*2.2,seed+time*.35))-.5;
        q.x+=curl*.7*(uv.y+.2);
        q.x/=mix(1.15,.40,smoothstep(.35,1.0,uv.y));
        float rr=dot(q,q);
        if(rr>=1.0)discard;
        float extent=sqrt(1.0-rr);
        vec3 accum=vec3(0);float opacity=0.0;
        for(int i=0;i<8;i++) {
            float z=mix(-extent,extent,(float(i)+.5)/8.0);
            vec3 pos=vec3(q,z);
            vec2 adv=vec2(pos.x*3.2+pos.z*1.7+seed,pos.y*3.8-time*2.8);
            float warp=noise(adv*.8+time*.17)-.5;
            float n=.68*noise(adv+vec2(warp*.9,0))+.32*noise(adv*2.3+pos.z*3.1);
            float shell=1.0-dot(pos,pos);
            float density=smoothstep(.08,.54,shell+(n-.5)*1.1)*(.45+.55*n);
            float a=1.0-exp(-density*extent*(.34+(1.0-heat)*.22));
            // Cooling lobes lose their hot core first, then darken into soot.
            vec3 emission=fireColor(clamp(-.18+shell*.85+n*.42-(1.0-heat)*.62,0.0,1.0));
            emission=mix(emission,vec3(.07,.045,.035)+emission*.25,smoothstep(.45,.05,heat)*(1.0-shell*.6));
            accum+=(1.0-opacity)*emission*a;
            opacity+=(1.0-opacity)*a;
        }
        color=accum/max(.001,opacity);
        alpha=opacity*parameters.a*smoothstep(0.0,.16,1.0-rr);
        // Warping must never expose the finite proxy rectangle.
        alpha*=smoothstep(0.0,.16,uv.x)*(1.0-smoothstep(.84,1.0,uv.x));
        alpha*=smoothstep(0.0,.08,uv.y)*(1.0-smoothstep(.88,1.0,uv.y));
    } else {
        float t=clamp(uv.y,0.0,1.0),x=(uv.x-.5)*2.0;
        // Two differently advected fields warp and erode the silhouette continuously.
        vec2 p=vec2(x*2.4+seed,t*5.5-time*2.5);
        float warp=flow(p*.75+vec2(0,time*.3))-.5;
        float n=flow(p+vec2(warp*1.5,0));
        float fine=noise(p*3.2+vec2(time*.5,seed));
        float width=1.0-.2*t+.32*warp;
        float density=width-abs(x+warp*.5)+(.5-n)*.7;
        float tip=1.0-smoothstep(.70,1.0,t+(n-.5)*.28);
        float base=smoothstep(0.0,.035,t);
        alpha=smoothstep(.015,.13,density)*tip*base*parameters.a;
        alpha*=mix(1.0,smoothstep(.22,.48,n),smoothstep(.2,.8,t)*.72);
        // Always vanish before the mesh boundary, even where noise increases density.
        // Otherwise a curved ribbon still exposes straight polygon edges in side views.
        alpha*=smoothstep(0.0,.13,uv.x)*(1.0-smoothstep(.87,1.0,uv.x));
        alpha*=1.0-smoothstep(.93,1.0,t);
        float ridge=1.0-abs(2.0*noise(p*1.7+vec2(warp*2.0,0)) -1.0);
        float temperature=clamp(.19+.76*n+.2*ridge-abs(x)*.26+(1.0-t)*.20+(fine-.5)*.16,0.0,1.0);
        color=fireColor(temperature);
        if(mode>.5 && mode<.6) {
            // Spark streak: white-gold hot head, cooling orange tail, soft across the width.
            alpha=exp(-x*x*4.0)*pow(t,1.4)*parameters.a;
            color=mix(vec3(1.0,.32,.03),vec3(1.0,.93,.62),clamp(heat*(.35+.65*t),0.0,1.0));
            color*=1.0+heat*.6;
        } else if(mode>.5) {
            // Round bloom falloff (no polygon edge), flickering slightly with the fire.
            vec2 c=(uv-.5)*2.0;
            float r2=dot(c,c);
            alpha=exp(-r2*3.2)*(1.0-smoothstep(.75,1.0,r2))*parameters.a*.30*(.88+.12*noise(vec2(time*6.0,seed)));
            color=mix(vec3(1,.29,.025),vec3(1,.62,.16),exp(-r2*6.0));
        } else if(mode>.42) {
            alpha=smoothstep(0.0,.15,uv.x)*(1.0-smoothstep(.85,1.0,uv.x))*sin(t*3.14159)*parameters.a;
            color=vec3(.88,.94,1.0);
        } else if(mode>.2) {
            alpha*=smoothstep(.22,.65,n)*.34;
            color=mix(vec3(.075,.065,.062),vec3(.22,.16,.105),heat*.45);
        }
        alpha*=smoothstep(0.0,.045,uv.x)*(1.0-smoothstep(.955,1.0,uv.x));
    }
    if(alpha<.003)discard;
    float fog=smoothstep(FogStart,max(FogStart+.001,FogEnd),viewDistance);
    fragColor=vec4(mix(color,FogColor.rgb,fog),alpha*(1.0-fog));
}
