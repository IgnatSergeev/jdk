with import <nixpkgs> {};
(pkgs.buildFHSEnv {
    name = "jdk";
    targetPkgs = pkgs: ([ 
      # Lsp, treesitter-parsers and debugger
      pkgs.clang-tools 
      pkgs.vimPlugins.nvim-treesitter-parsers.cmake 
      pkgs.vimPlugins.nvim-treesitter-parsers.cpp
      pkgs.vimPlugins.nvim-treesitter-parsers.c 
      pkgs.vimPlugins.nvim-treesitter-parsers.yaml
      pkgs.vimPlugins.nvim-treesitter-parsers.json
      pkgs.vimPlugins.nvim-treesitter-parsers.markdown
      pkgs.vimPlugins.nvim-treesitter-parsers.markdown_inline
      pkgs.vimPlugins.nvim-treesitter-parsers.nix
      pkgs.gdb

      # Build tools
      pkgs.clang
      pkgs.bear
      pkgs.libz
      pkgs.lld
      pkgs.autoconf
      pkgs.alsa-lib
      pkgs.cups
      pkgs.fontconfig
      pkgs.freetype

      pkgs.xorg.libX11
      pkgs.xorg.libXext
      pkgs.xorg.libXrender
      pkgs.xorg.libXrandr
      pkgs.xorg.libXtst
      pkgs.xorg.libXi
      pkgs.xorg.libXt
    ]);
    runScript = "zsh";
}).env
