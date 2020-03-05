package com.alibaba.csp.sentinel.cluster.redis.lua;

import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenResultStatus;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class LuaUtil {
    private static Map<String, String> luaCodeMapper = new HashMap<>();
    private static Map<String, String> luaShaMapper = new HashMap<>();

    // need reset lua sha when rebuild redis client
    public static void resetLuaSha() {
        synchronized (LuaUtil.class) {
            luaShaMapper.clear();
        }
    }

    public static String loadLuaCodeIfNeed(String luaId) {
        String lua = luaCodeMapper.get(luaId);
        if(lua == null) {
            synchronized (luaCodeMapper) {
                lua = luaCodeMapper.get(luaId);
                if(lua == null) {
                    lua = loadLua(luaId);
                    luaCodeMapper.put(luaId, lua);
                }
            }
        }
        return lua;
    }

    private static String loadLua(String luaId)  {
        try (InputStream input = LuaUtil.class.getResourceAsStream("/lua/" + luaId + ".lua")) {
            StringBuilder out = new StringBuilder();
            byte[] b = new byte[1024];
            for (int n; (n = input.read(b)) != -1; ) {
                out.append(new String(b, 0, n));
            }
            return out.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("cannot load luaCode:" + luaId,e);
        }
    }

    public static String loadLuaShaIfNeed(String luaId, long flowId, RedisScriptLoader scriptLoader) {
        return loadLuaShaIfNeed(luaId, flowId, null, scriptLoader);
    }

    public static String loadLuaShaIfNeed(String luaId, long flowId, Integer slot, RedisScriptLoader scriptLoader) {
        String cacheKey = luaId;
        if(slot != null) {
            cacheKey = cacheKey + slot;
        }

        String sha = luaShaMapper.get(cacheKey);
        if(sha == null) {
            synchronized (luaShaMapper) {
                sha = luaShaMapper.get(cacheKey);
                if(sha == null) {

                    String luaCode = loadLuaCodeIfNeed(luaId);
                    sha = scriptLoader.load(luaCode, flowId);
                    luaShaMapper.put(luaCode, sha);
                }
            }
        }
        return sha;
    }


    public static String toLuaParam(Object val, Object slotKey) {
        return  val + "{" + slotKey + "}" ;
    }

    public static int toTokenStatus(Object luaResult) {
        if(luaResult == null) {
            return TokenResultStatus.FAIL;
        } else {
            if(Integer.parseInt(luaResult.toString()) > 0) {
                return TokenResultStatus.OK;
            } else {
                return TokenResultStatus.BLOCKED;
            }
        }
    }
}
